package com.abservice.presentation.rest.site;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * サイト文言 REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code PUT /api/v1/site-contents/{key}}（登録・更新）と
 * {@code GET /api/v1/site-contents}（全件照会）の 疎通、検証エラーの 400、認証要求を確認する。実 DB（Flyway
 * migrate-at-start）で動作する。
 * </p>
 *
 * <p>
 * 初期データを持たない設計のため、テストは自分が投入したキーだけを見る（他のテストや手動投入の影響を受けない）。
 * </p>
 */
@QuarkusTest
@DisplayName("サイト文言 REST エンドポイントの統合テスト")
class SiteContentRestIntegrationTest {

    @Test
    @DisplayName("文言を登録すると全件照会に現れる")
    void upsertThenListContainsIt() {
        final String key = "test." + marker();

        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"## 紹介\",\"contentFormat\":\"MARKDOWN\"}")
                .when().put("/api/v1/site-contents/" + key).then().statusCode(200)
                .body("key", equalTo(key))
                .body("content", equalTo("## 紹介"))
                .body("contentFormat", equalTo("MARKDOWN"));

        given().when().get("/api/v1/site-contents").then().statusCode(200)
                .body("items.key", hasItem(key));
    }

    @Test
    @DisplayName("同じキーへの再登録は差し替えになり、件数が増えない")
    void upsertSameKeyReplacesContent() {
        final String key = "test." + marker();

        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"旧\",\"contentFormat\":\"PLAIN_TEXT\"}")
                .when().put("/api/v1/site-contents/" + key).then().statusCode(200);

        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"新\",\"contentFormat\":\"MARKDOWN\"}")
                .when().put("/api/v1/site-contents/" + key).then().statusCode(200)
                .body("content", equalTo("新"))
                .body("contentFormat", equalTo("MARKDOWN"));

        // 同じキーが2件並ばないこと（upsert であって追加ではない）
        given().when().get("/api/v1/site-contents").then().statusCode(200)
                .body("items.findAll { it.key == '" + key + "' }.size()", equalTo(1));
    }

    @Test
    @DisplayName("未登録のキーは照会結果に現れない")
    void unregisteredKeyIsAbsent() {
        final String key = "test." + marker();

        given().when().get("/api/v1/site-contents").then().statusCode(200)
                .body("items.key", not(hasItem(key)));
    }

    @Test
    @DisplayName("キーの形式違反は400 problem+jsonを返す")
    void invalidKeyFormatReturnsBadRequest() {
        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"x\",\"contentFormat\":\"PLAIN_TEXT\"}")
                .when().put("/api/v1/site-contents/InvalidKey").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("区切りのない1セグメントのキーは400を返す")
    void singleSegmentKeyReturnsBadRequest() {
        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"x\",\"contentFormat\":\"PLAIN_TEXT\"}")
                .when().put("/api/v1/site-contents/sitename").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("HTMLはマークアップ形式として受け付けず400を返す")
    void htmlFormatReturnsBadRequest() {
        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"<p>x</p>\",\"contentFormat\":\"HTML\"}")
                .when().put("/api/v1/site-contents/test." + marker()).then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("マークアップ形式の未指定は400を返す")
    void missingFormatReturnsBadRequest() {
        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"x\"}")
                .when().put("/api/v1/site-contents/test." + marker()).then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("登録・更新は認証を要求する")
    void upsertRequiresAuthentication() {
        given().contentType(ContentType.JSON)
                .body("{\"content\":\"x\",\"contentFormat\":\"PLAIN_TEXT\"}")
                .when().put("/api/v1/site-contents/test." + marker()).then().statusCode(401);
    }

    @Test
    @DisplayName("全件照会は認証を要求しない")
    void listDoesNotRequireAuthentication() {
        given().when().get("/api/v1/site-contents").then().statusCode(200);
    }

    /**
     * テスト間・実行間でキーが衝突しないようにする識別子。
     *
     * <p>
     * 先頭に英字を付けるのは、キーのセグメントが数字で始まることを許さないため（UUID の先頭は数字になりうるので、
     * そのまま使うと実行ごとに通る・落ちるが変わる）。
     * </p>
     *
     * @return 英字で始まる小文字英数字の識別子
     */
    private static String marker() {
        return "k" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
