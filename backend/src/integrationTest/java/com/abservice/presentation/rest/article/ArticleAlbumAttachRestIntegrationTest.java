package com.abservice.presentation.rest.article;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.hamcrest.Matchers.equalTo;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 記事へのアルバム紐付けの公開整合の統合テスト
 *
 * <p>
 * 「公開中の記事は公開中のアルバムだけを参照する」という不変条件を、紐付けの経路からも破らせない。公開中の記事へ非公開の アルバムを紐付けようとした場合は
 * 409 を返す。下書きの記事は制約を受けない。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("記事へのアルバム紐付けの公開整合の統合テスト")
class ArticleAlbumAttachRestIntegrationTest {

    @Test
    @DisplayName("公開中の記事に非公開のアルバムを紐付けると409 problem+jsonを返す")
    void attachingUnpublishedAlbumToPublishedArticleIsRejected() {
        final var albumId = createAlbum("紐付け整合テスト・非公開アルバム");
        final var articleId = createPublishedAlbumArticle("紐付け整合テスト・公開記事");

        attach(articleId, albumId).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("公開中の記事に公開中のアルバムは紐付けられる")
    void attachingPublishedAlbumToPublishedArticleIsAllowed() {
        final var albumId = createPublishedAlbum("紐付け整合テスト・公開アルバム");
        final var articleId = createPublishedAlbumArticle("紐付け整合テスト・公開記事（許可）");

        attach(articleId, albumId).then().statusCode(200)
                .body("albumId", equalTo(albumId));
    }

    @Test
    @DisplayName("下書きの記事には非公開のアルバムを紐付けられる")
    void attachingUnpublishedAlbumToDraftArticleIsAllowed() {
        final var albumId = createAlbum("紐付け整合テスト・下書き向け非公開アルバム");
        final var articleId = createAlbumArticle("紐付け整合テスト・下書き記事");

        attach(articleId, albumId).then().statusCode(200)
                .body("albumId", equalTo(albumId));
    }

    private static Response attach(String articleId, String albumId) {
        return authorized().contentType(ContentType.JSON)
                .body("{\"albumId\":\"%s\"}".formatted(albumId))
                .when().put("/api/v1/articles/" + articleId + "/album");
    }

    private static String createAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"%s\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"紐付け整合アーティスト\"}"
                                .formatted(title))
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static String createPublishedAlbum(String title) {
        final var albumId = createAlbum(title);
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);
        return albumId;
    }

    private static String createAlbumArticle(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"ALBUM\",\"title\":\"%s\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\"}"
                                .formatted(title))
                .when().post("/api/v1/articles").then().statusCode(201).extract().path("articleId");
    }

    private static String createPublishedAlbumArticle(String title) {
        final var articleId = createAlbumArticle(title);
        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200);
        return articleId;
    }
}
