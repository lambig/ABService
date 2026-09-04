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
         * 記事の削除は本体を返さない（Uni<Void>）。200 と空の本体で宣言されると、要求元は返らない本体を 読もうとする。201
         * の定義上の状態コードは別（#282）のため、ここでは 204 の操作だけを見る。
         */
        openApi()
                .body(responsesOf("delete", "/api/v1/articles/{id}"), hasKey("204"))
                .body(responsesOf("delete", "/api/v1/articles/{id}"), not(hasKey("200")))
                .body(responsesOf("delete", "/api/v1/articles/{id}") + ".'204'", not(hasKey("content")));
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
}
