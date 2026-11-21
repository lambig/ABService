package com.abservice.presentation.rest;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@DisplayName("一覧Queryの補正と入力エラーのHTTP契約")
class ListQueryInputRestIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/albums", "/api/v1/admin/albums", "/api/v1/articles",
            "/api/v1/admin/articles", "/api/v1/tunes"})
    @DisplayName("負のページと範囲外sizeは拒否せず補正した値を応答する")
    void outOfRangePaginationIsNormalized(String path) {
        authorized().queryParam("page", -1).queryParam("size", 0).when().get(path).then()
                .statusCode(200).body("page", equalTo(0)).body("size", equalTo(20));
        authorized().queryParam("size", 101).when().get(path).then()
                .statusCode(200).body("size", equalTo(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/albums", "/api/v1/admin/albums", "/api/v1/articles",
            "/api/v1/admin/articles", "/api/v1/tunes"})
    @DisplayName("キーと向きの同時不正はキーの400を先に返す")
    void sortFailurePrecedesDirection(String path) {
        authorized().queryParam("sort", "unknown").queryParam("direction", "sideways").when().get(path).then()
                .statusCode(400).contentType("application/problem+json")
                .body("errors", hasSize(1)).body("errors[0].field", equalTo("sort"))
                .body("errors[0].code", equalTo("SORT_KEY_NOT_USABLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/albums", "/api/v1/admin/albums", "/api/v1/articles",
            "/api/v1/admin/articles", "/api/v1/tunes"})
    @DisplayName("キー未指定でも不正な向きを400で返す")
    void invalidDirectionWithoutKeyIsRejected(String path) {
        authorized().queryParam("direction", "sideways").when().get(path).then()
                .statusCode(400).contentType("application/problem+json")
                .body("errors", hasSize(1)).body("errors[0].field", equalTo("direction"))
                .body("errors[0].code", equalTo("SORT_DIRECTION_NOT_USABLE"));
    }
}
