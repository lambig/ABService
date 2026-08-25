package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 集約をまたぐ操作を同時に行ったときの整合性の統合テスト
 *
 * <p>
 * 記事の公開可否はアルバムの公開状態に依存するが、判定と書き込みは別の集約に対して行われる。取得の時点でアルバムに対する
 * 主張（{@code AlbumAccessService}）を伴わなければ、判定から書き込みまでの間に相手が確定してしまい、
 * 「公開中の記事が非公開・削除済みのアルバムを参照する」状態が成立しうる。同時に走らせても、その状態にならないことを固定する。
 * </p>
 */
@QuarkusTest
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
            final var albumId = createPublishedAlbum("同時実行テストアルバム%d".formatted(attempt));
            final var articleId = createDraftAlbumArticle("同時実行テスト記事%d".formatted(attempt), albumId);

            runConcurrently(
                    () -> authorized().when().post("/api/v1/articles/" + articleId + "/publish"),
                    () -> authorized().when().post("/api/v1/albums/" + albumId + "/unpublish"));

            assertThat(List.of(articleIsPublic(articleId), albumIsPublished(albumId)))
                    .as("公開中の記事が非公開アルバムを参照している（attempt=%d）", attempt)
                    .isNotEqualTo(PUBLIC_ARTICLE_ON_UNPUBLISHED_ALBUM);
        });
    }

    @Test
    @DisplayName("記事へのアルバム紐付けとアルバム削除が同時に走っても、記事が存在しないアルバムを参照したまま公開中にならない")
    void concurrentAttachAndAlbumDeleteKeepConsistency() {
        IntStream.range(0, ATTEMPTS).forEach(attempt -> {
            final var albumId = createPublishedAlbum("削除競合テストアルバム%d".formatted(attempt));
            final var articleId = createDraftAlbumArticle("削除競合テスト記事%d".formatted(attempt), albumId);

            runConcurrently(
                    () -> authorized().when().post("/api/v1/articles/" + articleId + "/publish"),
                    () -> authorized().when().delete("/api/v1/albums/" + albumId));

            assertThat(List.of(articleIsPublic(articleId), albumExists(albumId)))
                    .as("公開中の記事が削除済みアルバムを参照している（attempt=%d）", attempt)
                    .isNotEqualTo(PUBLIC_ARTICLE_ON_UNPUBLISHED_ALBUM);
        });
    }

    private static void runConcurrently(Runnable first, Runnable second) {
        CompletableFuture.allOf(
                CompletableFuture.runAsync(first),
                CompletableFuture.runAsync(second))
                .join();
    }

    private static boolean articleIsPublic(String articleId) {
        return authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .extract().path("publicFlag");
    }

    private static boolean albumIsPublished(String albumId) {
        final String publishedAt = authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .extract().path("publishedAt");
        return publishedAt != null;
    }

    private static boolean albumExists(String albumId) {
        return authorized().when().get("/api/v1/admin/albums/" + albumId).then().extract().statusCode() == 200;
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

    private static String createDraftAlbumArticle(String title, String albumId) {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"ALBUM\",\"title\":\"%s\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\"}"
                                .formatted(title))
                .when().post("/api/v1/articles").then().statusCode(201).extract().path("articleId");
        authorized().contentType(ContentType.JSON)
                .body("{\"albumId\":\"%s\"}".formatted(albumId))
                .when().put("/api/v1/articles/" + articleId + "/album").then().statusCode(200);
        return articleId;
    }
}
