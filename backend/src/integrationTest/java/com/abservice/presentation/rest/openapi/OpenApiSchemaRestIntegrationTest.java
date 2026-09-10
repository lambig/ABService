package com.abservice.presentation.rest.openapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 生成される API 定義の E2E 統合テスト
 *
 * <p>
 * 応答の項目が「常にあるか」「null を取り得るか」は、record
 * の宣言から導く（{@link ResponseNullabilityFilter}）。
 * 利用側はこの定義から型を生成するため、実際の応答との対応をここで固定する。
 * </p>
 *
 * <p>
 * GENERATED-ARTIFACT-IS-NOT-A-CHECK:
 * 生成した型定義（{@code schema.d.ts}）はコミットされるが、組み立てで
 * 再生成して差分を見ていない。定義の退行は生成物に現れるだけで検査されないため、ここで直接固定する。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("生成される API 定義の統合テスト")
class OpenApiSchemaRestIntegrationTest {

    private static final String SCHEMAS = "components.schemas.";

    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

    /** エラー応答の本体を指す GPath。状態コードを差し込んで使う */
    private static final String PROBLEM_BODY = ".'%s'.content.'application/problem+json'.schema.$ref";

    @Test
    @DisplayName("応答の項目は値の有無によらず必須で、nullを取り得る項目だけが null 許容になる")
    void responsePropertiesAreRequiredAndNullableWhereDeclared() {
        openApi()
                // 値が無いことは null で表すため、項目名そのものは常にある
                .body(SCHEMAS + "PublicAlbumDetailResponse.required", hasItem("description"))
                .body(SCHEMAS + "PublicAlbumDetailResponse.required", hasItem("albumId"))
                // @Nullable の項目は null を取り得る
                .body(
                        SCHEMAS + "PublicAlbumDetailResponse.properties.description.type",
                        contains("string", "null"))
                // @Nullable でない項目は null を取らない
                .body(SCHEMAS + "PublicAlbumDetailResponse.properties.albumId.type", equalTo("string"));
    }

    @Test
    @DisplayName("他のスキーマを参照する項目の null 許容は参照とnullの選択で表す")
    void nullableReferenceIsExpressedAsChoice() {
        openApi()
                .body(SCHEMAS + "AdminAlbumArticleDetailResponse.required", hasItem("publishedAt"))
                .body(
                        SCHEMAS + "AdminAlbumArticleDetailResponse.properties.publishedAt.anyOf[0].$ref",
                        equalTo("#/components/schemas/Instant"))
                .body(
                        SCHEMAS + "AdminAlbumArticleDetailResponse.properties.publishedAt.anyOf[1].type",
                        equalTo("null"));
    }

    @Test
    @DisplayName("出力から省かれ得る項目は必須にしない")
    void omittedPropertiesAreNotRequired() {
        /*
         * ProblemDetail は @JsonInclude(NON_EMPTY) を持ち、空の errors と null の detail は
         * キーごと応答から消える。ここを必須にすると契約が実応答とずれる。
         */
        openApi().body(SCHEMAS + "ProblemDetail", not(hasKey("required")));
    }

    @Test
    @DisplayName("入れ子の応答 record も、項目が必須で返る")
    void nestedResponseRecordPropertiesAreRequired() {
        /*
         * NESTED-LOOKUP: 入れ子はスキーマ名（単純名）をパッケージへ繋いだ綴りで解決できないため、探索が 素通りすると required
         * ごと落ちる。直下の record だけを見ていると、その取りこぼしに気づけない。
         */
        openApi()
                .body(SCHEMAS + "PreconditionAffectedArticle", hasKey("required"))
                .body(
                        SCHEMAS + "PreconditionAffectedArticle.required",
                        containsInAnyOrder(
                                "articleId",
                                "title",
                                "losesAlbumReference",
                                "becomesUnpublished"));
    }

    @Test
    @DisplayName("Command の応答は本体の型を指す")
    void commandResponsesReferToTheirBodyType() {
        /*
         * BODY-TYPE: リソースが Response を返すと本体の型が定義に出ず、要求元が型を手書きすることになる。
         * 参照が具体のスキーマを指していることを固定する。
         */
        openApi()
                .body(
                        okBodyRefOf("put", "/api/v1/albums/{id}"),
                        equalTo("#/components/schemas/UpdateAlbumResponse"));
    }

    @Test
    @DisplayName("本体を持たない Command は 204 で、本体の宣言を持たない")
    void bodylessCommandRespondsWithNoContent() {
        /*
         * 記事の削除は本体を返さない（Uni<Void>）。200 と空の本体で宣言されると、要求元は返らない本体を 読もうとする。資源を作る 操作の 201 は
         * {@link #creatingCommandRespondsWithCreatedAndLocation} が見るため、ここでは 204
         * の操作だけを見る。
         */
        openApi()
                .body(responsesOf("delete", "/api/v1/articles/{id}"), hasKey("204"))
                .body(responsesOf("delete", "/api/v1/articles/{id}"), not(hasKey("200")))
                .body(responsesOf("delete", "/api/v1/articles/{id}") + ".'204'", not(hasKey("content")));
    }

    @Test
    @DisplayName("資源を作る操作は 201 と、作られた資源を指す Location を持つ")
    void creatingCommandRespondsWithCreatedAndLocation() {
        /*
         * CREATED-IS-NOT-IN-THE-RETURN-TYPE: 実装は RestResponse で 201 と Location を返すが、
         * smallrye は戻り値から状態コードもヘッダも読まない。定義が 200 のままだと、要求元は実在しない 200 を待ち、
         * 位置を型として受け取れない（#282）。集約直下と子資源の両方を見る。
         */
        openApi()
                .body(responsesOf("post", "/api/v1/albums"), hasKey("201"))
                .body(responsesOf("post", "/api/v1/albums"), not(hasKey("200")))
                .body(createdBodyRefOf("post", "/api/v1/albums"), equalTo("#/components/schemas/CreateAlbumResponse"))
                .body(locationOf("post", "/api/v1/albums") + ".required", equalTo(true))
                .body(locationOf("post", "/api/v1/albums") + ".schema.format", equalTo("uri-reference"))
                .body(responsesOf("post", "/api/v1/articles/{articleId}/tags"), hasKey("201"))
                .body(responsesOf("post", "/api/v1/articles/{articleId}/tags"), not(hasKey("200")))
                .body(locationOf("post", "/api/v1/articles/{articleId}/tags") + ".required", equalTo(true));
    }

    @Test
    @DisplayName("エラー応答はどの状態コードでも problem+json の ProblemDetail を本体に持つ")
    void errorResponsesCarryProblemDetail() {
        /*
         * ERROR-CONTRACT-IS-NOT-IN-THE-SIGNATURE: エラーは例外マッパーが返すため、リソースの
         * 戻り値型にも注釈にも現れない。要求元は定義から型を生成するので、状態コードだけがあって本体の型が無いと Problem Details
         * を型として読めない（生成物では content を持たない応答になる）。#282
         */
        openApi()
                // 管理操作は認証・認可の失敗を返す
                .body(responsesOf("post", "/api/v1/albums") + PROBLEM_BODY.formatted("401"), equalTo(PROBLEM_REF))
                .body(responsesOf("post", "/api/v1/albums") + PROBLEM_BODY.formatted("403"), equalTo(PROBLEM_REF))
                // 本体を受け取る操作は入力の検証失敗を返す
                .body(responsesOf("post", "/api/v1/albums") + PROBLEM_BODY.formatted("400"), equalTo(PROBLEM_REF))
                // 状態を変える操作は業務ルール違反と競合を返す
                .body(responsesOf("put", "/api/v1/albums/{id}") + PROBLEM_BODY.formatted("409"), equalTo(PROBLEM_REF))
                // パスで対象を指す操作は未存在を返す
                .body(responsesOf("put", "/api/v1/albums/{id}") + PROBLEM_BODY.formatted("404"), equalTo(PROBLEM_REF))
                // 想定外の失敗はどの操作でも起こり得る
                .body(responsesOf("get", "/api/v1/albums") + PROBLEM_BODY.formatted("500"), equalTo(PROBLEM_REF));
    }

    @Test
    @DisplayName("返らないエラーは定義に現れない（公開の読み取りに認証・認可・競合は無い）")
    void unreachableErrorsAreAbsent() {
        /*
         * 全オペレーションへ一律に足すと、返らない状態コードを契約として宣言することになる。要求元はそれを
         * 扱う枝を書くため、宣言する範囲は返し得る条件と対で決める。
         */
        openApi()
                .body(responsesOf("get", "/api/v1/albums"), not(hasKey("401")))
                .body(responsesOf("get", "/api/v1/albums"), not(hasKey("403")))
                .body(responsesOf("get", "/api/v1/albums"), not(hasKey("409")))
                // 並び順を問合せ文字列で受け取るため、入力の検証失敗は返し得る
                .body(responsesOf("get", "/api/v1/albums"), hasKey("400"))
                // パスで対象を指すだけの操作は入力を受け取らない
                .body(responsesOf("post", "/api/v1/albums/{id}/publish"), not(hasKey("400")))
                .body(responsesOf("post", "/api/v1/albums/{id}/publish"), hasKey("409"));
    }

    @Test
    @DisplayName("記事の応答は articleType の値から実装スキーマを引ける")
    void articleResponseIsDiscriminatedByArticleType() {
        openApi()
                .body(SCHEMAS + "PublicArticleResponse.discriminator.propertyName", equalTo("articleType"))
                .body(
                        SCHEMAS + "PublicArticleResponse.discriminator.mapping.ALBUM",
                        equalTo("#/components/schemas/PublicAlbumArticleResponse"))
                .body(
                        SCHEMAS + "PublicArticleResponse.discriminator.mapping.NOTE",
                        equalTo("#/components/schemas/PublicPlainArticleResponse"))
                .body(SCHEMAS + "PublicAlbumArticleResponse.properties.articleType.enum", contains("ALBUM"));
    }

    private static ValidatableResponse openApi() {
        return given().accept("application/json").when().get("/q/openapi?format=json").then().statusCode(200);
    }

    /*
     * GPATH-QUOTING: 経路と状態コードはそのままでは GPath の識別子にならない（`/`・`{}` を含み、状態コードは
     * 数字で始まる）。引用して1つのキーとして扱う。
     */
    private static String responsesOf(String method, String path) {
        return "paths.'%s'.%s.responses".formatted(path, method);
    }

    private static String okBodyRefOf(String method, String path) {
        return responsesOf(method, path) + ".'200'.content.'application/json'.schema.$ref";
    }

    private static String createdBodyRefOf(String method, String path) {
        return responsesOf(method, path) + ".'201'.content.'application/json'.schema.$ref";
    }

    private static String locationOf(String method, String path) {
        return responsesOf(method, path) + ".'201'.headers.Location";
    }
}
