package com.abservice.presentation.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OpenAPI 文書の統合テスト
 *
 * <p>
 * API定義はJAX-RSの型情報から生成し、型から導けないもの（メタ情報・認証方式）だけを {@link OpenApiDefinition}
 * で宣言する。ここで固定するのはその生成結果の要点、すなわち 認証方式が Bearer の HTTP
 * スキームとして定義されること、管理操作に認証要件が付き公開向け照会には付かないこと。
 * </p>
 */
@QuarkusTest
@DisplayName("OpenAPI 文書の統合テスト")
class OpenApiRestIntegrationTest {

    @Test
    @DisplayName("認証方式はBearerのHTTPスキームとして定義される（JWT形式とはしない）")
    void securitySchemeIsHttpBearer() {
        given().accept("application/json").when().get("/q/openapi").then().statusCode(200)
                .body("components.securitySchemes.adminApiKey.type", equalTo("http"))
                .body("components.securitySchemes.adminApiKey.scheme", equalTo("bearer"))
                .body("components.securitySchemes.adminApiKey.bearerFormat", nullValue())
                .body("info.title", equalTo("ABService API"));
    }

    @Test
    @DisplayName("管理操作には認証要件が付き、公開向け照会には付かない")
    void securityRequirementFollowsRolesAllowed() {
        given().accept("application/json").when().get("/q/openapi").then().statusCode(200)
                .body("paths.'/api/v1/albums'.post.security[0].adminApiKey", equalTo(List.of("admin")))
                .body("paths.'/api/v1/admin/albums'.get.security[0].adminApiKey", equalTo(List.of("admin")))
                .body("paths.'/api/v1/albums'.get.security", nullValue());
    }

    @Test
    @DisplayName("外部音源・アセットのエンドポイントも定義に含まれる")
    void childResourcesAreDocumented() {
        given().accept("application/json").when().get("/q/openapi").then().statusCode(200)
                .body("paths.'/api/v1/albums/{albumId}/external-audios'.post", notNullValue())
                .body("paths.'/api/v1/assets/upload-url'.post", notNullValue());
    }

    @Test
    @DisplayName("YAML形式でも取得できる")
    void yamlIsServed() {
        given().when().get("/q/openapi").then().statusCode(200).body(containsString("openapi:"));
    }
}
