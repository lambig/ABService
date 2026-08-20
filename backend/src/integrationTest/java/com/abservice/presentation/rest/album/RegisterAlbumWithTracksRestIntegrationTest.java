package com.abservice.presentation.rest.album;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバムとその初期トラック一覧のワンリクエスト登録 REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/albums/with-tracks} の疎通、トラックを含まない登録、トラック検証エラー・
 * トラック番号重複時にアルバム自体も登録されない（トランザクション全体がロールバックされる）ことを、実 DB（Flyway
 * migrate-at-start）で確認する。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバムとトラックのワンリクエスト登録 REST エンドポイントの統合テスト")
class RegisterAlbumWithTracksRestIntegrationTest {

    @Test
    @DisplayName("トラックを含めて登録すると201でアルバムとトラック情報が返る")
    void registerWithTracksSucceeds() {
        given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"ワンリクエスト登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"tracks\":["
                                + "{\"trackNo\":1,\"title\":\"1曲目\"},"
                                + "{\"trackNo\":2,\"title\":\"2曲目\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(201)
                .body("title", equalTo("ワンリクエスト登録アルバム")).body("tracks.size()", equalTo(2))
                .body("tracks[0].trackNo", equalTo(1)).body("tracks[0].title", equalTo("1曲目"))
                .body("tracks[1].trackNo", equalTo(2)).body("tracks[1].title", equalTo("2曲目"));
    }

    @Test
    @DisplayName("トラックを指定しなければトラックなしで登録される")
    void registerWithoutTracksSucceeds() {
        given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"トラックなし登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(201)
                .body("title", equalTo("トラックなし登録アルバム")).body("tracks", empty());
    }

    @Test
    @DisplayName("アルバムのタイトルが空白なら400 problem+json（検証エラー）を返す")
    void albumValidationError() {
        given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"   \",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\","
                                + "\"tracks\":[{\"trackNo\":1,\"title\":\"1曲目\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("トラック番号が重複していると409 problem+jsonを返す")
    void duplicateTrackNoReturnsConflict() {
        given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"トラック番号重複登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"tracks\":["
                                + "{\"trackNo\":1,\"title\":\"1曲目\"},"
                                + "{\"trackNo\":1,\"title\":\"別の1曲目\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("トラックのタイトルが未指定だと400 problem+json（検証エラー）を返す")
    void trackValidationError() {
        given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"トラック検証エラー登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"tracks\":[{\"trackNo\":1}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }
}
