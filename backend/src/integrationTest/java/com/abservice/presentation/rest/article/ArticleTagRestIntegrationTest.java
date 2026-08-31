package com.abservice.presentation.rest.article;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 記事タグ REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/articles/{id}/tags}（付与）→ {@code DELETE
 * /api/v1/articles/{id}/tags/{tagId}}（除去）と、 付いたタグが公開向け・管理向けの詳細照会に現れること、管理向けの
 * {@code GET /api/v1/admin/article-tags}（語彙の一覧）を確認する。 タグは複数の記事が共有する語彙のため、同じ名前を
 * 別の記事へ付けても同じタグになる。実 DB（Flyway migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@DisplayName("記事タグ REST エンドポイントの統合テスト")
class ArticleTagRestIntegrationTest {

    @Test
    @DisplayName("タグを付けると201を返し、管理向け詳細に現れる")
    void addTagAppearsInAdminDetail() {
        final var articleId = createDraftArticle("タグ付与確認記事");
        final var name = uniqueName("付与");

        final String tagId = addTag(articleId, name).then().statusCode(201)
                .body("articleId", equalTo(articleId))
                .body("name", equalTo(name))
                .extract().path("tagId");

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("tags.tagId", hasItem(tagId))
                .body("tags.name", hasItem(name));
    }

    @Test
    @DisplayName("同じ名前のタグを別の記事に付けても同じタグになる")
    void sameNameIsSharedAcrossArticles() {
        final var name = uniqueName("共有");
        final var firstId = createDraftArticle("タグ共有確認記事1");
        final var secondId = createDraftArticle("タグ共有確認記事2");

        final String firstTagId = addTag(firstId, name).then().statusCode(201).extract().path("tagId");
        final String secondTagId = addTag(secondId, name).then().statusCode(201).extract().path("tagId");

        assertThat(secondTagId).isEqualTo(firstTagId);
    }

    @Test
    @DisplayName("同じタグを同じ記事へ二重に付けると409 problem+jsonを返す")
    void addingSameTagTwiceIsRejected() {
        final var articleId = createDraftArticle("タグ重複確認記事");
        final var name = uniqueName("重複");
        addTag(articleId, name).then().statusCode(201);

        addTag(articleId, name).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("タグを外すと204を返し、詳細から消える")
    void removeTagDisappearsFromDetail() {
        final var articleId = createDraftArticle("タグ除去確認記事");
        final var name = uniqueName("除去");
        final String tagId = addTag(articleId, name).then().statusCode(201).extract().path("tagId");

        authorized().when().delete("/api/v1/articles/" + articleId + "/tags/" + tagId).then().statusCode(204);

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("tags", empty());
    }

    @Test
    @DisplayName("付いていないタグを外してもべき等に204を返す")
    void removingUnattachedTagIsIdempotent() {
        final var articleId = createDraftArticle("タグ除去べき等確認記事");
        final var name = uniqueName("べき等");
        final String tagId = addTag(articleId, name).then().statusCode(201).extract().path("tagId");

        authorized().when().delete("/api/v1/articles/" + articleId + "/tags/" + tagId).then().statusCode(204);
        authorized().when().delete("/api/v1/articles/" + articleId + "/tags/" + tagId).then().statusCode(204);
    }

    @Test
    @DisplayName("公開向け詳細はタグ名だけを名前の昇順で返し、タグIDを持たない")
    void publicDetailReturnsTagNamesOnly() {
        final var articleId = createDraftArticle("タグ公開表示確認記事");
        final var marker = UUID.randomUUID().toString().substring(0, 8);
        final var second = marker + "-2";
        final var first = marker + "-1";
        addTag(articleId, second).then().statusCode(201);
        addTag(articleId, first).then().statusCode(201);
        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200);

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(200)
                .body("tags", contains(first, second));

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("tags[0]", hasKey("tagId"))
                .body("tags[0].name", equalTo(first));
    }

    @Test
    @DisplayName("一覧はタグの項目自体を返さない")
    void listDoesNotReturnTags() {
        final var articleId = createDraftArticle("タグ一覧非返却確認記事");
        addTag(articleId, uniqueName("一覧")).then().statusCode(201);

        final var item = "items.find { it.articleId == '" + articleId + "' }";
        authorized().when().get("/api/v1/admin/articles?page=0&size=100").then().statusCode(200)
                .body(item, not(hasKey("tags")));
    }

    @Test
    @DisplayName("管理向けのタグ一覧は語彙を名前の昇順で返す")
    void adminTagListReturnsVocabularySortedByName() {
        final var articleId = createDraftArticle("タグ語彙一覧確認記事");
        final var marker = UUID.randomUUID().toString().substring(0, 8);
        final var second = marker + "-b";
        final var first = marker + "-a";
        addTag(articleId, second).then().statusCode(201);
        addTag(articleId, first).then().statusCode(201);

        final List<String> names = authorized().when().get("/api/v1/admin/article-tags").then().statusCode(200)
                .extract().path("items.name");

        assertThat(names.indexOf(first)).isLessThan(names.indexOf(second));
    }

    @Test
    @DisplayName("タグ名が空白なら400 problem+json（検証エラー）を返す")
    void blankTagNameIsRejected() {
        final var articleId = createDraftArticle("タグ名検証確認記事");

        addTag(articleId, "   ").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].code", equalTo("TAG_NAME_REQUIRED"));
    }

    @Test
    @DisplayName("存在しない記事へのタグ付与は404 problem+jsonを返す")
    void addingTagToUnknownArticleIsNotFound() {
        authorized().contentType(ContentType.JSON).body("{\"name\":\"存在しない記事のタグ\"}")
                .when().post("/api/v1/articles/" + UUID.randomUUID() + "/tags").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("タグ操作と語彙一覧は認証を要求する")
    void tagOperationsRequireAuthentication() {
        final var articleId = createDraftArticle("タグ認証確認記事");

        given().contentType(ContentType.JSON).body("{\"name\":\"認証確認\"}")
                .when().post("/api/v1/articles/" + articleId + "/tags").then().statusCode(401);

        given().when().delete("/api/v1/articles/" + articleId + "/tags/" + UUID.randomUUID()).then().statusCode(401);

        given().when().get("/api/v1/admin/article-tags").then().statusCode(401);
    }

    private static Response addTag(String articleId, String name) {
        return authorized().contentType(ContentType.JSON)
                .body("{\"name\":\"" + name + "\"}")
                .when().post("/api/v1/articles/" + articleId + "/tags");
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String createDraftArticle(String title) {
        return authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"" + title + "\"}").when().post("/api/v1/articles")
                .then().statusCode(201).extract().path("articleId");
    }
}
