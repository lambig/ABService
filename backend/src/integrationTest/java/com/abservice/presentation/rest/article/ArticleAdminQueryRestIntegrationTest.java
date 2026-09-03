package com.abservice.presentation.rest.article;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
@ExtendWith(CleanDatabase.class)
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
    @DisplayName("公開後の記事は管理向け詳細でのみ公開フラグを持ち、公開向けでは項目名自体が現れない")
    void publishedArticleIsFlaggedOnlyInAdminDetail() {
        final var articleId = createDraftArticle("管理Query公開後記事");

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200);

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("publicFlag", equalTo(true));

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(200)
                .body("$", not(hasKey("publicFlag")));
    }

    @Test
    @DisplayName("管理向け詳細は編集フォームが使う項目を持ち、値が無いことは null で表す")
    void adminDetailKeepsEditableFields() {
        final var articleId = createDraftArticle("管理Query編集項目記事");

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("body", equalTo(""))
                .body("bodyFormat", equalTo("PLAIN_TEXT"))
                // 本文とショート紹介文は「無い」ことがあり得ない項目のため、空は null ではなく空文字列で表す
                .body("introShort", equalTo(""))
                // 値が無いことを表す null はキーを出す（項目名を落とすのは概念を持たない場合だけ）
                .body("$", hasKey("publishedAt"))
                .body("publishedAt", nullValue())
                .body("$", hasKey("updatedAtBusiness"));
    }

    @Test
    @DisplayName("アルバム記事の管理向け詳細は、参照の失効に関わる項目名を持つ")
    void adminDetailKeepsAlbumReferenceKeys() {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"ALBUM\",\"title\":\"管理Query失効項目記事\"}").when()
                .post("/api/v1/articles").then().statusCode(201).extract().path("articleId");

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("$", hasKey("albumId"))
                .body("$", hasKey("formerAlbumId"))
                .body("$", hasKey("albumReferenceLostAt"))
                .body("$", hasKey("albumReferenceLostReason"));
    }

    @Test
    @DisplayName("管理向け一覧は本文を返さず、作業に必要な公開状態と更新日時を返す")
    void adminListReturnsWorkflowFields() {
        final var articleId = createDraftArticle("管理Query一覧項目記事");

        final var item = "items.find { it.articleId == '" + articleId + "' }";
        authorized().when().get("/api/v1/admin/articles?page=0&size=100").then().statusCode(200)
                .body(item + ".publicFlag", equalTo(false))
                .body(item, hasKey("updatedAtBusiness"))
                .body(item, not(hasKey("body")))
                .body(item, not(hasKey("introShort")))
                .body(item, not(hasKey("albumId")));
    }

    @Test
    @DisplayName("管理向け一覧は参照先アルバムで絞り込める")
    void adminListFiltersByAlbumId() {
        final var albumId = createDraftAlbum("管理Query絞り込みアルバム");
        final var linkedId = createAlbumArticle("管理Query絞り込み対象記事", albumId);
        final var unlinkedId = createDraftArticle("管理Query絞り込み対象外記事");

        authorized().queryParam("albumId", albumId).queryParam("size", 100)
                .when().get("/api/v1/admin/articles").then().statusCode(200)
                .body("items.articleId", hasItem(linkedId))
                .body("items.articleId", not(hasItem(unlinkedId)));
    }

    @Test
    @DisplayName("参照を持たないアルバムでの絞り込みは空の一覧を返す")
    void adminListReturnsEmptyForAlbumWithoutArticles() {
        final var albumId = createDraftAlbum("管理Query参照なしアルバム");

        authorized().queryParam("albumId", albumId)
                .when().get("/api/v1/admin/articles").then().statusCode(200)
                .body("totalElements", equalTo(0))
                .body("items", empty());
    }

    /*
     * 非公開化のカスケード対象は参照記事のうち公開中のものに限る（UnpublishAlbumService）。モーダルが見る集合が
     * その条件と一致することを、公開と下書きが混在する状態で固定する。
     */
    @Test
    @DisplayName("公開状態での絞り込みは非公開化のカスケード対象だけを返す")
    void adminListFiltersByPublicFlagForCascadeTarget() {
        final var albumId = createPublishedAlbum("管理Queryカスケード確認アルバム");
        final var publishedId = createAlbumArticle("管理Queryカスケード対象記事", albumId);
        authorized().when().post("/api/v1/articles/" + publishedId + "/publish").then().statusCode(200);
        final var draftId = createAlbumArticle("管理Queryカスケード対象外記事", albumId);

        authorized().queryParam("albumId", albumId).queryParam("size", 100)
                .when().get("/api/v1/admin/articles").then().statusCode(200)
                .body("totalElements", equalTo(2))
                .body("items.articleId", hasItem(publishedId))
                .body("items.articleId", hasItem(draftId));

        authorized().queryParam("albumId", albumId).queryParam("publicFlag", true).queryParam("size", 100)
                .when().get("/api/v1/admin/articles").then().statusCode(200)
                .body("totalElements", equalTo(1))
                .body("items.articleId", hasItem(publishedId))
                .body("items.articleId", not(hasItem(draftId)));
    }

    @Test
    @DisplayName("参照記事が下書きだけならカスケード対象は0件になる")
    void adminListReturnsNoCascadeTargetWhenAllReferencingArticlesAreDrafts() {
        final var albumId = createDraftAlbum("管理Query下書きのみ参照アルバム");
        createAlbumArticle("管理Query下書きのみ参照記事", albumId);

        authorized().queryParam("albumId", albumId).queryParam("publicFlag", true)
                .when().get("/api/v1/admin/articles").then().statusCode(200)
                .body("totalElements", equalTo(0))
                .body("items", empty());
    }

    @Test
    @DisplayName("管理向け一覧は業務上の更新日時で並べられる")
    void adminListSortsByBusinessUpdatedAt() {
        final var older = createDraftArticle("管理Query更新順記事1");
        final var newer = createDraftArticle("管理Query更新順記事2");

        final List<String> ids = authorized().queryParam("sort", "updatedAtBusiness").queryParam("size", 100)
                .when().get("/api/v1/admin/articles").then().statusCode(200)
                .extract().path("items.articleId");

        assertThat(ids.indexOf(newer)).isLessThan(ids.indexOf(older));
    }

    @Test
    @DisplayName("業務上の更新日時は公開向けの並び順には使えない")
    void businessUpdatedAtIsNotUsableOnPublicApi() {
        given().queryParam("sort", "updatedAtBusiness").when().get("/api/v1/articles").then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].field", equalTo("sort"))
                .body("errors[0].code", equalTo("SORT_KEY_NOT_USABLE"));
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

    private static String createPublishedAlbum(String title) {
        final String albumId = createDraftAlbum(title);
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);
        return albumId;
    }

    private static String createDraftAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"テストアーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static String createAlbumArticle(String title, String albumId) {
        final String articleId = authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"ALBUM\",\"title\":\"" + title + "\"}").when().post("/api/v1/articles")
                .then().statusCode(201).extract().path("articleId");
        authorized().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}")
                .when().put("/api/v1/articles/" + articleId + "/album").then().statusCode(200);
        return articleId;
    }
}
