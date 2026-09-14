package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 作品を曲目・外部音源ごと登録する REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/albums/with-tracks} の疎通、子を含まない登録、子の検証エラー・URL重複時に作品自体も
 * 登録されない（トランザクション全体がロールバックされる）ことを、実 DB（Flyway migrate-at-start）で確認する。
 * </p>
 *
 * <p>
 * 要求は番号を運ばない（#391）。トラック番号もチューンの登場順も表示順も、送られた配列の位置から振られる。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("アルバムとトラックのワンリクエスト登録 REST エンドポイントの統合テスト")
class RegisterAlbumWithTracksRestIntegrationTest {

    @Test
    @DisplayName("トラックを含めて登録すると201でアルバムとトラック情報が返る")
    void registerWithTracksSucceeds() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"ワンリクエスト登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"tracks\":["
                                + "{\"title\":\"1曲目\"},"
                                + "{\"title\":\"2曲目\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(201)
                .body("title", equalTo("ワンリクエスト登録アルバム")).body("tracks.size()", equalTo(2))
                .body("tracks[0].trackNo", equalTo(1)).body("tracks[0].title", equalTo("1曲目"))
                .body("tracks[1].trackNo", equalTo(2)).body("tracks[1].title", equalTo("2曲目"));
    }

    @Test
    @DisplayName("ワンリクエスト登録は201と、登録したアルバムを指すLocationを返す")
    void registerRespondsWithCreatedAndLocation() {
        final var response = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"位置確認の一括登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\","
                                + "\"tracks\":[{\"title\":\"1曲目\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(201).extract();

        assertThat(response.header("Location")).isEqualTo("/api/v1/albums/" + response.path("albumId"));
    }

    @Test
    @DisplayName("トラックを指定しなければトラックなしで登録される")
    void registerWithoutTracksSucceeds() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"トラックなし登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(201)
                .body("title", equalTo("トラックなし登録アルバム")).body("tracks", empty());
    }

    @Test
    @DisplayName("アルバムのタイトルが空白なら400 problem+json（検証エラー）を返す")
    void albumValidationError() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"   \",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\","
                                + "\"tracks\":[{\"title\":\"1曲目\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("同じ外部音源のURLが並びに2度現れると409 problem+jsonを返す")
    void duplicateExternalAudioUrlReturnsConflict() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"音源重複登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"externalAudios\":["
                                + "{\"url\":\"https://soundcloud.com/example/first\"},"
                                + "{\"url\":\"https://soundcloud.com/example/first\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("外部音源も並びごと受け取り、表示順は配列の位置から振られる")
    void externalAudiosAreRegisteredInTheGivenOrder() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"音源つき登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"externalAudios\":["
                                + "{\"url\":\"https://soundcloud.com/example/second\"},"
                                + "{\"url\":\"https://soundcloud.com/example/first\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(201)
                .extract().path("albumId");
    }

    @Test
    @DisplayName("チューン構成の行そのものが無いと400で、その行の位置を返す")
    void missingTuneRowReturnsValidationError() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"チューン行欠落登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"tracks\":["
                                + "{\"title\":\"1曲目\",\"tunes\":[null]}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("tracks[0].tunes[0]"))
                .body("errors[0].code", equalTo("TUNE_REQUIRED"));
    }

    @Test
    @DisplayName("トラックのタイトルが未指定だと400 problem+json（検証エラー）を返す")
    void trackValidationError() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"トラック検証エラー登録アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"tracks\":[{}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }
}
