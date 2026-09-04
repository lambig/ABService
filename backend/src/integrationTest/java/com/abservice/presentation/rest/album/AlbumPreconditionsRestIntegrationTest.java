package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * アルバムに対する操作の前提を問う照会の統合テスト
 *
 * <p>
 * 管理画面が破壊的な操作の前に「この操作をして問題ないか」を問う経路（#274）。実行前に返る影響が、実行後に返る影響と
 * 一致することを確かめる——一致しなければ、管理画面が見せる事前確認が実際の結果とずれる。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("アルバム操作の前提照会の統合テスト")
class AlbumPreconditionsRestIntegrationTest {

    @Test
    @DisplayName("削除の前提として、参照している記事と起きることが返る")
    void deletionPreconditionsListAffectedArticles() {
        final var albumId = createPublishedAlbum("削除前提テストアルバム");
        final var publishedId = createPublishedAlbumArticle("削除前提テスト公開記事", albumId);
        final var draftId = createAlbumArticle("削除前提テスト下書き記事", albumId);

        authorized().when().get("/api/v1/admin/albums/" + albumId + "/preconditions?operation=delete")
                .then().statusCode(200)
                .body("operation", equalTo("delete"))
                .body("unpublication", nullValue())
                .body("deletion.affectedArticles.size()", equalTo(2))
                .body("deletion.affectedArticles.articleId", hasItems(publishedId, draftId))
                .body(
                        "deletion.affectedArticles.find { it.articleId == '%s' }.becomesUnpublished"
                                .formatted(publishedId),
                        equalTo(true))
                .body(
                        "deletion.affectedArticles.find { it.articleId == '%s' }.becomesUnpublished"
                                .formatted(draftId),
                        equalTo(false))
                .body(
                        "deletion.affectedArticles.find { it.articleId == '%s' }.losesAlbumReference"
                                .formatted(draftId),
                        equalTo(true));
    }

    @Test
    @DisplayName("非公開化の前提として、連動して非公開になる記事だけが返る")
    void unpublicationPreconditionsListOnlyPublicArticles() {
        final var albumId = createPublishedAlbum("非公開前提テストアルバム");
        final var publishedId = createPublishedAlbumArticle("非公開前提テスト公開記事", albumId);
        createAlbumArticle("非公開前提テスト下書き記事", albumId);

        authorized().when().get("/api/v1/admin/albums/" + albumId + "/preconditions?operation=unpublish")
                .then().statusCode(200)
                .body("operation", equalTo("unpublish"))
                .body("deletion", nullValue())
                .body("unpublication.articlesBecomingUnpublished.size()", equalTo(1))
                .body("unpublication.articlesBecomingUnpublished[0].articleId", equalTo(publishedId));
    }

    /**
     * 事前に問うた内容が、実行後に返る内容と一致することを確かめる。ここがずれると事前確認の意味が失われる。
     */
    @Test
    @DisplayName("削除の前提として返った記事は、実際に削除したときの影響一覧と一致する")
    void deletionPreconditionsMatchTheActualOutcome() {
        final var albumId = createPublishedAlbum("一致テストアルバム");
        final var publishedId = createPublishedAlbumArticle("一致テスト公開記事", albumId);
        final var draftId = createAlbumArticle("一致テスト下書き記事", albumId);

        authorized().when().get("/api/v1/admin/albums/" + albumId + "/preconditions?operation=delete")
                .then().statusCode(200)
                .body("deletion.affectedArticles.articleId", hasItems(publishedId, draftId));

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("affectedArticles.size()", equalTo(2))
                .body("affectedArticles.articleId", hasItems(publishedId, draftId));
    }

    @Test
    @DisplayName("参照している記事がなければ影響は空で返る")
    void preconditionsOfUnreferencedAlbumAreEmpty() {
        final var albumId = createPublishedAlbum("参照なし前提テストアルバム");

        authorized().when().get("/api/v1/admin/albums/" + albumId + "/preconditions?operation=delete")
                .then().statusCode(200)
                .body("deletion.affectedArticles.size()", equalTo(0));
    }

    @Test
    @DisplayName("下書きのアルバムでも前提を問える")
    void preconditionsAreAvailableForDraftAlbum() {
        final var albumId = createAlbum("下書き前提テストアルバム");

        authorized().when().get("/api/v1/admin/albums/" + albumId + "/preconditions?operation=delete")
                .then().statusCode(200)
                .body("deletion.affectedArticles.size()", equalTo(0));
    }

    @Test
    @DisplayName("存在しないアルバムの前提は 404 で返る")
    void preconditionsOfMissingAlbumReturnNotFound() {
        authorized().when()
                .get("/api/v1/admin/albums/01999999-9999-7999-8999-999999999999/preconditions?operation=delete")
                .then().statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("許可外の操作は 400 で返る")
    void unusableOperationIsRejected() {
        final var albumId = createAlbum("許可外操作テストアルバム");

        authorized().when().get("/api/v1/admin/albums/" + albumId + "/preconditions?operation=publish")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].field", equalTo("operation"))
                .body("errors[0].code", equalTo("ALBUM_OPERATION_NOT_USABLE"));
    }

    @Test
    @DisplayName("操作の指定が無ければ 400 で返る")
    void missingOperationIsRejected() {
        final var albumId = createAlbum("操作未指定テストアルバム");

        authorized().when().get("/api/v1/admin/albums/" + albumId + "/preconditions")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].code", equalTo("ALBUM_OPERATION_REQUIRED"));
    }

    @Test
    @DisplayName("資格情報が無ければ前提は問えない")
    void preconditionsRequireCredentials() {
        final var albumId = createAlbum("認証テストアルバム");

        given().when().get("/api/v1/admin/albums/" + albumId + "/preconditions?operation=delete")
                .then().statusCode(401);
    }

    private static String createAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"%s\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"前提テストアーティスト\"}"
                                .formatted(title))
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static String createPublishedAlbum(String title) {
        final var albumId = createAlbum(title);
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
        return articleId;
    }
}
