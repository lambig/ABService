package com.abservice.presentation.rest.article;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 記事の編集にも編集開始時点を条件に取る契約（DECISIONS 30 / #309）の E2E 統合テスト
 *
 * <p>
 * 契約そのものはアルバムで確立した（{@code AlbumEditRevisionRestIntegrationTest}）。ここで見ているのは、
 * 記事の更新にも同じ形が適用され、方式を作り直していないことである。
 * </p>
 *
 * <p>
 * どこまでが同じ編集単位かもここで固定する。タグの付け替えは記事そのものの更新ではないため、本体の世代も更新日時も
 * 動かさない（アルバムのトラックと同じ扱い）。一方、公開・非公開は記事の状態を変えるため世代が進む。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("記事の編集世代の契約の統合テスト")
class ArticleEditRevisionRestIntegrationTest {

    private static String createArticle(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"NOTE\",\"title\":\"" + title + "\",\"body\":\"本文\","
                                + "\"bodyFormat\":\"MARKDOWN\"}")
                .when().post("/api/v1/articles").then().statusCode(201).extract().path("articleId");
    }

    /** 管理詳細が返す編集の世代。画面はこれをそのまま更新へ返す */
    private static int revisionOf(String articleId) {
        return authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200).extract()
                .path("revision");
    }

    private static String updateBody(int expectedRevision, String title) {
        return "{\"expectedRevision\":" + expectedRevision + ",\"articleType\":\"NOTE\",\"title\":\"" + title
                + "\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\"}";
    }

    @Test
    @DisplayName("2つのタブが同じ詳細を読んだ後、後から届いた古いフォームは409になり、先の保存が残る")
    void staleFormIsRejectedAndTheFirstSaveSurvives() {
        final String articleId = createArticle("世代テスト記事");

        final int revisionInTabA = revisionOf(articleId);
        final int revisionInTabB = revisionOf(articleId);

        authorized().contentType(ContentType.JSON).body(updateBody(revisionInTabA, "タブAの保存"))
                .when().put("/api/v1/articles/" + articleId).then().statusCode(200);

        authorized().contentType(ContentType.JSON).body(updateBody(revisionInTabB, "タブBの保存"))
                .when().put("/api/v1/articles/" + articleId).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:CONFLICTING_UPDATE"));

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("title", equalTo("タブAの保存"));
    }

    @Test
    @DisplayName("最新の世代なら保存でき、応答と詳細の世代が進む")
    void latestRevisionSavesAndAdvancesTheRevision() {
        final String articleId = createArticle("世代前進記事");
        final int revision = revisionOf(articleId);

        final int savedRevision = authorized().contentType(ContentType.JSON)
                .body(updateBody(revision, "世代前進記事（改題）"))
                .when().put("/api/v1/articles/" + articleId).then().statusCode(200).extract().path("revision");

        assertThat(savedRevision).as("保存で世代が進む").isGreaterThan(revision);
        assertThat(revisionOf(articleId)).as("詳細も進んだ世代を返す").isEqualTo(savedRevision);

        authorized().contentType(ContentType.JSON).body(updateBody(savedRevision, "世代前進記事（再改題）"))
                .when().put("/api/v1/articles/" + articleId).then().statusCode(200);
    }

    @Test
    @DisplayName("世代を伴わない更新は400で、位置は expectedRevision になる")
    void updateWithoutExpectedRevisionIsRejected() {
        final String articleId = createArticle("世代なし更新記事");

        authorized().contentType(ContentType.JSON)
                .body("{\"articleType\":\"NOTE\",\"title\":\"世代なしの保存\"}")
                .when().put("/api/v1/articles/" + articleId).then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("expectedRevision"))
                .body("errors[0].code", equalTo("ARTICLE_EXPECTED_REVISION_REQUIRED"));

        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("title", equalTo("世代なし更新記事"));
    }

    @Test
    @DisplayName("タグの操作は記事本体の世代も更新日時も動かさない（タグの付け替えは記事の更新ではない）")
    void changingTagsDoesNotAdvanceTheArticleRevision() {
        final String articleId = createArticle("タグの編集単位記事");
        final int revision = revisionOf(articleId);
        final String updatedAtBusiness = authorized().when().get("/api/v1/admin/articles/" + articleId).then()
                .statusCode(200).extract().path("updatedAtBusiness");

        final String tagId = authorized().contentType(ContentType.JSON).body("{\"name\":\"世代テストタグ\"}")
                .when().post("/api/v1/articles/" + articleId + "/tags").then().statusCode(201).extract()
                .path("tagId");

        authorized().when().delete("/api/v1/articles/" + articleId + "/tags/" + tagId).then().statusCode(204);

        assertThat(revisionOf(articleId)).as("タグの付け替えでは本体の世代は進まない").isEqualTo(revision);
        authorized().when().get("/api/v1/admin/articles/" + articleId).then().statusCode(200)
                .body("updatedAtBusiness", equalTo(updatedAtBusiness));

        /* したがって、タグを操作した後も本体の編集は読み直しを要さない */
        authorized().contentType(ContentType.JSON).body(updateBody(revision, "タグ操作後の保存"))
                .when().put("/api/v1/articles/" + articleId).then().statusCode(200);
    }

    @Test
    @DisplayName("公開すると記事本体の世代が進み、公開前の世代での保存は競合になる")
    void publishingAdvancesTheRevision() {
        final String articleId = createArticle("公開と世代の記事");
        final int revisionBeforePublish = revisionOf(articleId);

        authorized().when().post("/api/v1/articles/" + articleId + "/publish").then().statusCode(200);

        assertThat(revisionOf(articleId)).as("公開は本体を変えるため世代が進む")
                .isGreaterThan(revisionBeforePublish);

        authorized().contentType(ContentType.JSON).body(updateBody(revisionBeforePublish, "公開前の世代での保存"))
                .when().put("/api/v1/articles/" + articleId).then().statusCode(409);
    }
}
