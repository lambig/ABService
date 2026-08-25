package com.abservice.presentation.rest.exception;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.hibernate.StaleStateException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 楽観ロック競合の扱いの統合テスト
 *
 * <p>
 * 全テーブルが {@code version} 列を持ち同一行への同時更新を検出する（{@code docs/DECISIONS.md} 5）。検出した
 * 競合が想定外障害（500）ではなく契約上の競合（409）として現れることを固定する。
 * </p>
 *
 * <p>
 * 1件目は Hibernate Reactive が実際に投げる例外の型とラップ形を固定する（マッパーがどの型を受けるべきかの根拠）。
 * 2件目は同じ記事へ同時に更新をかけ、応答が 200 か 409 に収まる（500 にならない）ことを確かめる。
 * </p>
 *
 * <p>
 * 2件目は競合の発生自体を要求しない（片方が先に完了しきれば両方200になりうる）。ただし実測では毎回一方が409になり、
 * 409の経路が実際に通ることを確認している。競合が起きるかどうかを断定しないのは、記事の更新にも直列化を入れる判断が
 * 将来出た場合に、正しい変更でテストが落ちないようにするため。
 * </p>
 */
@QuarkusTest
@DisplayName("楽観ロック競合の統合テスト")
class ConflictingUpdateRestIntegrationTest {

    /** 競合の窓は数ミリ秒のため、1回では取りこぼす。繰り返して当たりを増やす */
    private static final int ATTEMPTS = 5;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    private Mutiny.SessionFactory sessionFactory;

    @Inject
    private Pool pool;

    @Test
    @DisplayName("別トランザクションが更新した行へflushすると OptimisticLockException（原因は StaleStateException）が投げられる")
    void staleFlushThrowsOptimisticLockException() {
        final var articleId = createArticle("競合検出テスト記事");

        final var failure = catchThrowable(() -> staleFlush(articleId).await().atMost(TIMEOUT));

        assertThat(failure)
                .isInstanceOf(OptimisticLockException.class)
                .hasCauseInstanceOf(StaleStateException.class);
    }

    @Test
    @DisplayName("同じ記事への同時更新は200か409で返り、500にはならない")
    void concurrentUpdatesNeverReturnServerError() {
        IntStream.range(0, ATTEMPTS).forEach(attempt -> {
            final var articleId = createArticle("同時更新テスト記事%d".formatted(attempt));

            final var statuses = runConcurrently(
                    () -> update(articleId, "同時更新A%d".formatted(attempt)),
                    () -> update(articleId, "同時更新B%d".formatted(attempt)));

            assertThat(statuses)
                    .as("同時更新の応答（競合は409、500は不可。attempt=%d）", attempt)
                    .allSatisfy(status -> assertThat(status).isIn(200, 409));
        });
    }

    /**
     * 読み込みと flush の間に別トランザクションの更新を挟み、確実に競合させる。
     *
     * @param articleId
     *            対象記事のドメインID
     * @return flush の結果（競合により失敗する）
     */
    private Uni<Void> staleFlush(String articleId) {
        return sessionFactory.withTransaction(
                session -> loadArticle(session, articleId)
                        .flatMap(
                                entity -> bumpVersionInAnotherTransaction(articleId)
                                        .invoke(() -> entity.setTitle("stale write"))
                                        .chain(session::flush)));
    }

    private static Uni<ArticleTableRecord> loadArticle(Mutiny.Session session, String articleId) {
        return session
                .createQuery(
                        "SELECT a FROM ArticleTableRecord a WHERE a.domainId = :domainId",
                        ArticleTableRecord.class)
                .setParameter("domainId", articleId).getSingleResult();
    }

    /*
     * SEPARATE-CONNECTION: Hibernateのセッションを介さないプールの問い合わせは自動コミットのため、
     * 呼び出し元のトランザクションとは別のトランザクションとして先にコミットされる。
     */
    private Uni<Void> bumpVersionInAnotherTransaction(String articleId) {
        return pool.preparedQuery("UPDATE article SET version = version + 1 WHERE domain_id = $1")
                .execute(Tuple.of(articleId))
                .replaceWithVoid();
    }

    private static List<Integer> runConcurrently(Supplier<Integer> first, Supplier<Integer> second) {
        final var firstStatus = CompletableFuture.supplyAsync(first);
        final var secondStatus = CompletableFuture.supplyAsync(second);
        return List.of(firstStatus.join(), secondStatus.join());
    }

    private static int update(String articleId, String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"NEWS\",\"title\":\"%s\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\"}"
                                .formatted(title))
                .when().put("/api/v1/articles/" + articleId).getStatusCode();
    }

    private static String createArticle(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"NEWS\",\"title\":\"%s\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\"}"
                                .formatted(title))
                .when().post("/api/v1/articles").then().statusCode(201).extract().path("articleId");
    }
}
