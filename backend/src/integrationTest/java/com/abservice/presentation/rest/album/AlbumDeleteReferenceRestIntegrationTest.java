package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバム削除に伴う記事のアルバム参照失効の統合テスト
 *
 * <p>
 * アルバムは物理削除するため、参照していた記事は参照先を失う。公開中の記事は自動で非公開へ戻し、参照は失効状態
 * （旧アルバムID・失効日時・理由）へ遷移させ、影響を受けた記事を応答に含める。失効した参照を持つアルバム記事は 再公開できない（前進方向の規則）。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバム削除に伴う記事参照失効の統合テスト")
class AlbumDeleteReferenceRestIntegrationTest {

    @Test
    @DisplayName("公開中の記事が参照するアルバムを削除すると、記事は自動で非公開になり参照が失効する")
    void deletingAlbumUnpublishesAndDetachesReferencingArticle() {
        final var albumId = createPublishedAlbum("参照失効テストアルバム");
        final var articleId = createPublishedAlbumArticle("参照失効テスト記事", albumId);

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("affectedArticles[0].articleId", equalTo(articleId))
                .body("affectedArticles[0].unpublished", equalTo(true));

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("publicFlag", equalTo(false))
                .body("albumId", nullValue())
                .body("formerAlbumId", equalTo(albumId))
                .body("albumReferenceLostAt", notNullValue())
                .body("albumReferenceLostReason", equalTo("ALBUM_DELETED"));
    }

    @Test
    @DisplayName("失効した参照を持つアルバム記事は再公開できない")
    void articleWithLostAlbumReferenceCannotBeRepublished() {
        final var albumId = createPublishedAlbum("再公開拒否テストアルバム");
        final var articleId = createPublishedAlbumArticle("再公開拒否テスト記事", albumId);

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200);

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("下書きの記事が参照するアルバムを削除しても、非公開化はされず参照だけが失効する")
    void deletingAlbumOnlyDetachesDraftArticle() {
        final var albumId = createPublishedAlbum("下書き参照テストアルバム");
        final var articleId = createAlbumArticle("下書き参照テスト記事", albumId);

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("affectedArticles[0].articleId", equalTo(articleId))
                .body("affectedArticles[0].unpublished", equalTo(false));

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("albumId", nullValue())
                .body("formerAlbumId", equalTo(albumId))
                .body("albumReferenceLostReason", equalTo("ALBUM_DELETED"));
    }

    @Test
    @DisplayName("参照している記事がなければ影響一覧は空で返る")
    void deletingUnreferencedAlbumAffectsNothing() {
        final var albumId = createPublishedAlbum("参照なしテストアルバム");

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("affectedArticles.size()", equalTo(0));
    }

    private static String createPublishedAlbum(String title) {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"%s\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"参照失効アーティスト\"}"
                                .formatted(title))
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);
        return albumId;
    }

    private static String createAlbumArticle(String title, String albumId) {
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

    private static String createPublishedAlbumArticle(String title, String albumId) {
        final var articleId = createAlbumArticle(title, albumId);
        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200);
        given().when().get("/api/v1/articles/" + articleId).then().statusCode(200);
        return articleId;
    }
}
