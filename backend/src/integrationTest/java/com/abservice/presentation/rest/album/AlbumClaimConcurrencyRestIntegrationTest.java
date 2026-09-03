package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 集約をまたぐ操作を同時に行ったときの整合性の統合テスト
 *
 * <p>
 * 記事の公開可否と紐付け可否はアルバムの状態に依存するが、判定と書き込みは別の集約に対して行われる。取得の時点で
 * アルバムに対する主張（{@code AlbumAccessService}）を伴わなければ、判定から書き込みまでの間に相手が確定してしまい、
 * 「公開中の記事が非公開アルバムを参照する」「存在しないアルバムへの参照が残る」状態が成立しうる。同時に走らせても それらの状態にならないことを固定する。
 * </p>
 *
 * <p>
 * 終状態だけでなく各操作の応答コードも検証する。競合が想定外の障害（500）として現れる形は、終状態が禁止パターンでなくても 回帰とみなすため。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("集約をまたぐ操作の同時実行の統合テスト")
class AlbumClaimConcurrencyRestIntegrationTest {

    /** 競合の窓は数ミリ秒のため、1回の実行では踏み外しを取りこぼす。繰り返して当たりを増やす */
    private static final int ATTEMPTS = 5;

    /** 公開中の記事が非公開のアルバムを参照している状態（成立してはならない組み合わせ） */
    private static final List<Boolean> PUBLIC_ARTICLE_ON_UNPUBLISHED_ALBUM = List.of(true, false);

    @Test
    @DisplayName("記事の公開とアルバムの非公開化が同時に走っても、公開中の記事が非公開アルバムを参照する状態にならない")
    void concurrentArticlePublishAndAlbumUnpublishKeepConsistency() {
        IntStream.range(0, ATTEMPTS).forEach(attempt -> {
            final var albumId = createPublishedAlbum("非公開競合テストアルバム%d".formatted(attempt));
            final var articleId = createDraftAlbumArticle("非公開競合テスト記事%d".formatted(attempt));
            attachAlbum(articleId, albumId);

            final var statuses = runConcurrently(
                    () -> post("/api/v1/articles/" + articleId + "/publish"),
                    () -> post("/api/v1/albums/" + albumId + "/unpublish"));

            assertThat(statuses.getFirst())
                    .as("記事の公開（アルバムが先に非公開化されれば規則違反の409、attempt=%d）", attempt)
                    .isIn(200, 409);
            assertThat(statuses.getLast())
                    .as("アルバムの非公開化（attempt=%d）", attempt)
                    .isEqualTo(200);

            assertThat(List.of(articleIsPublic(articleId), albumIsPublished(albumId)))
                    .as("公開中の記事が非公開アルバムを参照している（attempt=%d）", attempt)
                    .isNotEqualTo(PUBLIC_ARTICLE_ON_UNPUBLISHED_ALBUM);
        });
    }

    @Test
    @DisplayName("記事の公開とアルバム削除が同時に走っても、記事は公開されず有効なアルバム参照も残らない")
    void concurrentArticlePublishAndAlbumDeleteKeepConsistency() {
        IntStream.range(0, ATTEMPTS).forEach(attempt -> {
            final var albumId = createPublishedAlbum("削除競合テストアルバム%d".formatted(attempt));
            final var articleId = createDraftAlbumArticle("削除競合テスト記事%d".formatted(attempt));
            attachAlbum(articleId, albumId);

            final var statuses = runConcurrently(
                    () -> post("/api/v1/articles/" + articleId + "/publish"),
                    () -> delete("/api/v1/albums/" + albumId));

            assertThat(statuses.getFirst())
                    .as("記事の公開（アルバムが先に消えれば参照先未存在の404か失効の409、attempt=%d）", attempt)
                    .isIn(
                            200,
                            404,
                            409);
            assertThat(statuses.getLast())
                    .as("アルバムの削除（べき等、attempt=%d）", attempt)
                    .isEqualTo(200);

            assertThat(articleIsPublic(articleId))
                    .as("削除済みアルバムを参照する記事が公開中のまま残っている（attempt=%d）", attempt)
                    .isFalse();
            assertThat(articleAlbumId(articleId))
                    .as("削除済みアルバムへの有効な参照が残っている（attempt=%d）", attempt)
                    .isNull();
        });
    }

    @Test
    @DisplayName("記事へのアルバム紐付けとアルバム削除が同時に走っても、存在しないアルバムへの有効な参照が残らない")
    void concurrentAttachAndAlbumDeleteKeepConsistency() {
        IntStream.range(0, ATTEMPTS).forEach(attempt -> {
            final var albumId = createPublishedAlbum("紐付け競合テストアルバム%d".formatted(attempt));
            final var articleId = createDraftAlbumArticle("紐付け競合テスト記事%d".formatted(attempt));

            final var statuses = runConcurrently(
                    () -> attachAlbumStatus(articleId, albumId),
                    () -> delete("/api/v1/albums/" + albumId));

            assertThat(statuses.getFirst())
                    .as("アルバムの紐付け（アルバムが先に消えれば参照先未存在の404、attempt=%d）", attempt)
                    .isIn(200, 404);
            assertThat(statuses.getLast())
                    .as("アルバムの削除（べき等、attempt=%d）", attempt)
                    .isEqualTo(200);

            assertThat(articleAlbumId(articleId))
                    .as("存在しないアルバムへの有効な参照が残っている（attempt=%d）", attempt)
                    .isNull();
        });
    }

    private static List<Integer> runConcurrently(Supplier<Integer> first, Supplier<Integer> second) {
        final var firstStatus = CompletableFuture.supplyAsync(first);
        final var secondStatus = CompletableFuture.supplyAsync(second);
        return List.of(firstStatus.join(), secondStatus.join());
    }

    private static int post(String path) {
        return authorized().when().post(path).getStatusCode();
    }

    private static int delete(String path) {
        return authorized().when().delete(path).getStatusCode();
    }

    private static int attachAlbumStatus(String articleId, String albumId) {
        return authorized().contentType(ContentType.JSON)
                .body("{\"albumId\":\"%s\"}".formatted(albumId))
                .when().put("/api/v1/articles/" + articleId + "/album").getStatusCode();
    }

    private static void attachAlbum(String articleId, String albumId) {
        assertThat(attachAlbumStatus(articleId, albumId)).isEqualTo(200);
    }

    private static boolean articleIsPublic(String articleId) {
        return authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .extract().path("publicFlag");
    }

    private static String articleAlbumId(String articleId) {
        return authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .extract().path("albumId");
    }

    private static boolean albumIsPublished(String albumId) {
        final String publishedAt = authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .extract().path("publishedAt");
        return publishedAt != null;
    }

    private static String createPublishedAlbum(String title) {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"%s\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"同時実行アーティスト\"}"
                                .formatted(title))
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);
        return albumId;
    }

    private static String createDraftAlbumArticle(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"ALBUM\",\"title\":\"%s\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\"}"
                                .formatted(title))
                .when().post("/api/v1/articles").then().statusCode(201).extract().path("articleId");
    }
}
