package com.abservice.presentation.rest.openapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
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
}
