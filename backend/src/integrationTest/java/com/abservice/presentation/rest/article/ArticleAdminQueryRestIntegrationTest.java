package com.abservice.presentation.rest.article;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 記事管理向け Query REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code GET /api/v1/admin/articles}（一覧）と {@code GET
 * /api/v1/admin/articles/{id}}（詳細）が
 * 下書き（未公開）記事を返し、同じ記事が公開向け（{@code /api/v1/articles}）からは見えないことを確認する。実 DB （Flyway
 * migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@DisplayName("記事管理向け Query REST エンドポイントの統合テスト")
class ArticleAdminQueryRestIntegrationTest {

    @Test
    @DisplayName("下書き記事は管理向け詳細では取得できるが公開向けでは404になる")
    void draftIsVisibleOnlyToAdminDetail() {
        final var articleId = createDraftArticle("管理Query下書き詳細記事");

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("articleId", equalTo(articleId)).body("title", equalTo("管理Query下書き詳細記事"))
                .body("publicFlag", equalTo(false));

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("下書き記事は管理向け一覧には含まれるが公開向け一覧には含まれない")
    void draftIsVisibleOnlyToAdminList() {
        final var articleId = createDraftArticle("管理Query下書き一覧記事");

        authorized().when().get("/api/v1/admin/articles?page=0&size=100").then().statusCode(200)
                .body("items.articleId", hasItem(articleId));

        given().when().get("/api/v1/articles?page=0&size=100").then().statusCode(200)
                .body("items.articleId", not(hasItem(articleId)));
    }

    @Test
    @DisplayName("公開後の記事は管理向け詳細でも公開フラグ付きで取得できる")
    void publishedArticleIsFlaggedInAdminDetail() {
        final var articleId = createDraftArticle("管理Query公開後記事");

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200);

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("publicFlag", equalTo(true));

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(200)
                .body("publicFlag", equalTo(true));
    }

    @Test
    @DisplayName("存在しないIDの管理向け詳細は404 problem+jsonを返す")
    void adminDetailNotFound() {
        authorized().when().get("/api/v1/admin/articles/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    private static String createDraftArticle(String title) {
        return authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"" + title + "\"}").when().post("/api/v1/articles")
                .then().statusCode(201).extract().path("articleId");
    }
}
