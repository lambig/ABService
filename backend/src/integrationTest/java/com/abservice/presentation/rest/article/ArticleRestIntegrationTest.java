package com.abservice.presentation.rest.article;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 記事 REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/articles}（作成）→ {@code GET /api/v1/articles/{id}}（詳細）→
 * {@code PUT /api/v1/articles/{id}}（更新）→ {@code PUT
 * /api/v1/articles/{id}/album} （アルバム紐付け）→ {@code POST
 * /api/v1/articles/{id}/publish} （公開）→ {@code POST
 * /api/v1/articles/{id}/unpublish}（非公開化）→ {@code DELETE
 * /api/v1/articles/{id}}（削除）の疎通と、 {@code GET
 * /api/v1/articles}（一覧、ページネーション付き）、未存在時の 404、検証エラー時の 400 を RFC 9457 Problem
 * Details 込みで確認する。実 DB（Flyway migrate-at-start）で動作する。GET
 * 単体取得・一覧取得は認証を伴わない公開向けQueryのため、作成直後の下書き（非公開）記事は 404／一覧除外となる（下書きを含めた
 * 閲覧は認証必須の別経路で提供予定、#116）。アルバム非公開化に伴うカスケード非公開化は
 * {@code UnpublishAlbumServiceIntegrationTest}で検証する。
 * </p>
 */
@QuarkusTest
@DisplayName("記事 REST エンドポイントの統合テスト")
class ArticleRestIntegrationTest {

    @Test
    @DisplayName("記事を作成すると下書き状態になり、公開向けAPIでのID詳細取得は404になる")
    void createThenGetIsNotFoundWhileUnpublished() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"NOTE\",\"title\":\"E2Eテスト記事\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\","
                                + "\"introShort\":\"概要\"}")
                .when().post("/api/v1/articles").then().statusCode(201).body("articleType", equalTo("NOTE"))
                .body("title", equalTo("E2Eテスト記事")).body("publicFlag", equalTo(false)).extract()
                .path("articleId");

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("存在しないIDは404 problem+jsonを返す")
    void getNotFound() {
        given().when().get("/api/v1/articles/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"))
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("タイトル空白は400 problem+json（検証エラー）を返す")
    void createValidationError() {
        authorized().contentType(ContentType.JSON).body("{\"articleType\":\"NOTE\",\"title\":\"   \"}").when()
                .post("/api/v1/articles").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR")).body("status", equalTo(400))
                .body("errors", not(empty())).body("errors[0].field", equalTo("title"));
    }

    @Test
    @DisplayName("記事を更新すると全項目置換され、公開状態は変化しない（下書きのままなので公開向けGETは404）")
    void updateReplacesFieldsAndPreservesPublicFlag() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"NOTE\",\"title\":\"更新前タイトル\",\"body\":\"更新前本文\","
                                + "\"bodyFormat\":\"MARKDOWN\",\"introShort\":\"更新前概要\"}")
                .when().post("/api/v1/articles").then().statusCode(201).extract().path("articleId");

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"NOTE\",\"title\":\"更新後タイトル\",\"body\":\"更新後本文\","
                                + "\"bodyFormat\":\"PLAIN_TEXT\",\"introShort\":\"更新後概要\"}")
                .when().put("/api/v1/articles/" + articleId).then().statusCode(200)
                .body("articleId", equalTo(articleId)).body("title", equalTo("更新後タイトル"))
                .body("publicFlag", equalTo(false));

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(404);
    }

    @Test
    @DisplayName("存在しないIDの更新は404 problem+jsonを返す")
    void updateNotFound() {
        authorized().contentType(ContentType.JSON).body("{\"articleType\":\"NOTE\",\"title\":\"タイトル\"}").when()
                .put("/api/v1/articles/" + UUID.randomUUID()).then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("タイトル空白での更新は400 problem+json（検証エラー）を返す")
    void updateValidationError() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"タイトル\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().contentType(ContentType.JSON).body("{\"articleType\":\"NOTE\",\"title\":\"   \"}").when()
                .put("/api/v1/articles/" + articleId).then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("title"));
    }

    @Test
    @DisplayName("記事を公開すると公開向けGETで参照できるようになる")
    void publishThenGetSucceeds() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"公開確認記事\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200)
                .body("articleId", equalTo(articleId)).body("publicFlag", equalTo(true));

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(200)
                .body("articleId", equalTo(articleId));
    }

    @Test
    @DisplayName("公開済み記事を非公開化すると公開向けGETは404になる")
    void unpublishThenGetNotFound() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"非公開化確認記事\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200);

        authorized().when().post("/api/v1/articles/" + articleId + "/unpublish").then().statusCode(200)
                .body("articleId", equalTo(articleId)).body("publicFlag", equalTo(false));

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(404);
    }

    @Test
    @DisplayName("存在しないIDの公開は404 problem+jsonを返す")
    void publishNotFound() {
        authorized().when().post("/api/v1/articles/" + UUID.randomUUID() + "/publish").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("存在しないIDの非公開化は404 problem+jsonを返す")
    void unpublishNotFound() {
        authorized().when().post("/api/v1/articles/" + UUID.randomUUID() + "/unpublish").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("ALBUM種別の記事にアルバムを紐付けられる（参照先が下書きでも紐付け自体は成功する）")
    void setAlbumSucceedsForAlbumTypeArticle() {
        final String albumId = createDraftAlbum("紐付け確認アルバム");
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"ALBUM\",\"title\":\"紐付け確認記事\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}").when()
                .put("/api/v1/articles/" + articleId + "/album").then().statusCode(200)
                .body("articleId", equalTo(articleId)).body("albumId", equalTo(albumId));
    }

    @Test
    @DisplayName("NOTE種別の記事へのアルバム紐付けは409 problem+jsonを返す")
    void setAlbumFailsForNonAlbumTypeArticle() {
        final String albumId = createDraftAlbum("紐付け拒否確認アルバム");
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"紐付け拒否確認記事\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}").when()
                .put("/api/v1/articles/" + articleId + "/album").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("存在しないアルバムへの紐付けは404 problem+jsonを返す")
    void setAlbumNotFoundForUnknownAlbum() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"ALBUM\",\"title\":\"存在しないアルバム紐付け確認記事\"}").when()
                .post("/api/v1/articles").then().statusCode(201).extract().path("articleId");

        authorized().contentType(ContentType.JSON).body("{\"albumId\":\"" + UUID.randomUUID() + "\"}").when()
                .put("/api/v1/articles/" + articleId + "/album").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("存在しない記事への紐付けは404 problem+jsonを返す")
    void setAlbumNotFoundForUnknownArticle() {
        final String albumId = createDraftAlbum("記事不在確認アルバム");

        authorized().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}").when()
                .put("/api/v1/articles/" + UUID.randomUUID() + "/album").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("下書きAlbumへ紐付けた記事は公開できないが、Album公開後は公開できる")
    void publishFailsUntilReferencedAlbumIsPublished() {
        final String albumId = createDraftAlbum("公開制御確認アルバム");
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"ALBUM\",\"title\":\"公開制御確認記事\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}").when()
                .put("/api/v1/articles/" + articleId + "/album").then().statusCode(200);

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));

        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200)
                .body("publicFlag", equalTo(true));
    }

    private static String createDraftAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    @Test
    @DisplayName("記事を削除すると以後のGETは404になる")
    void deleteThenGetNotFound() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"削除対象\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().when().delete("/api/v1/articles/" + articleId).then().statusCode(204);

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(404);
    }

    @Test
    @DisplayName("削除はべき等で、存在しないIDの削除も204を返す")
    void deleteIsIdempotent() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"べき等確認\"}").when().post("/api/v1/articles").then()
                .statusCode(201).extract().path("articleId");

        authorized().when().delete("/api/v1/articles/" + articleId).then().statusCode(204);
        authorized().when().delete("/api/v1/articles/" + articleId).then().statusCode(204);
        authorized().when().delete("/api/v1/articles/" + UUID.randomUUID()).then().statusCode(204);
    }

    @Test
    @DisplayName("一覧（公開向けAPI）は下書き記事を含まず、件数は作成しても増加しない")
    void listExcludesUnpublishedArticles() {
        final int before = given().when().get("/api/v1/articles?page=0&size=1").then().statusCode(200).extract()
                .path("totalElements");

        final List<String> createdIds = List.of(
                createArticle("一覧確認記事1"),
                createArticle("一覧確認記事2"),
                createArticle("一覧確認記事3"));

        final var response = given().when().get("/api/v1/articles?page=0&size=100").then().statusCode(200)
                .body("totalElements", equalTo(before)).extract();

        final List<String> ids = response.path("items.articleId");
        assertThat(ids).doesNotContainAnyElementsOf(createdIds);
    }

    @Test
    @DisplayName("一覧のpage/sizeを省略するとデフォルト値（page=0, size=20）が使われる")
    void listUsesDefaultPageAndSizeWhenOmitted() {
        given().when().get("/api/v1/articles").then().statusCode(200).body("page", equalTo(0))
                .body("size", equalTo(20));
    }

    private static String createArticle(String title) {
        return authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"" + title + "\"}").when().post("/api/v1/articles")
                .then().statusCode(201).extract().path("articleId");
    }
}
