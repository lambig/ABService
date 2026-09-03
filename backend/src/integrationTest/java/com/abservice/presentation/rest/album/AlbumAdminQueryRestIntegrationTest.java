package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * アルバム管理向け Query REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code GET /api/v1/admin/albums}（一覧）と {@code GET
 * /api/v1/admin/albums/{id}}（詳細）が下書き
 * （未公開）アルバムを返し、同じアルバムが公開向け（{@code /api/v1/albums}）からは見えないことを確認する。実 DB （Flyway
 * migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("アルバム管理向け Query REST エンドポイントの統合テスト")
class AlbumAdminQueryRestIntegrationTest {

    @Test
    @DisplayName("下書きアルバムは管理向け詳細では取得できるが公開向けでは404になる")
    void draftIsVisibleOnlyToAdminDetail() {
        final var albumId = createDraftAlbum("管理Query下書き詳細");

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("title", equalTo("管理Query下書き詳細"))
                .body("publishedAt", nullValue());

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("下書きアルバムは管理向け一覧には含まれるが公開向け一覧には含まれない")
    void draftIsVisibleOnlyToAdminList() {
        final var albumId = createDraftAlbum("管理Query下書き一覧");

        authorized().when().get("/api/v1/admin/albums?page=0&size=100").then().statusCode(200)
                .body("items.albumId", hasItem(albumId));

        given().when().get("/api/v1/albums?page=0&size=100").then().statusCode(200)
                .body("items.albumId", not(hasItem(albumId)));
    }

    @Test
    @DisplayName("公開後のアルバムは管理向け詳細でも公開日時付きで取得できる")
    void publishedAlbumHasPublishedAtInAdminDetail() {
        final var albumId = createDraftAlbum("管理Query公開後");

        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("publishedAt", notNullValue());

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("publishedAt", notNullValue());
    }

    @Test
    @DisplayName("管理向け詳細は下書きのまま曲目をチューン構成つきで返す")
    void adminDetailReturnsTracksWithTunes() {
        final var albumId = createDraftAlbum("管理Query曲目");
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"trackNo\":1,\"title\":\"1曲目\",\"tunes\":["
                                + "{\"seq\":1,\"tuneTitle\":\"チューン1\",\"arrangerCreditOverride\":\"Arranger\"}]}")
                .when().post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(201);

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("publishedAt", nullValue()).body("tracks[0].title", equalTo("1曲目"))
                .body("tracks[0].tunes[0].tuneTitle", equalTo("チューン1"))
                .body("tracks[0].tunes[0].arrangerCreditOverride", equalTo("Arranger"));
    }

    @Test
    @DisplayName("管理向け詳細は編集フォームが使うソートキーを返す")
    void adminDetailKeepsEditingOnlyKeys() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"管理Query編集項目\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"テストアーティスト\",\"artistSortKey\":\"てすとあーてぃすと\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"trackNo\":1,\"title\":\"1曲目\",\"artistDisplayName\":\"トラックアーティスト\","
                                + "\"artistSortKey\":\"とらっくあーてぃすと\"}")
                .when().post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(201);

        authorized().contentType(ContentType.JSON)
                .body("{\"url\":\"https://soundcloud.com/example/admin-key-check\"}")
                .when().post("/api/v1/albums/" + albumId + "/external-audios").then().statusCode(201);

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("artistSortKey", equalTo("てすとあーてぃすと"))
                .body("tracks[0].artistSortKey", equalTo("とらっくあーてぃすと"))
                // 編集対象を同定するためのIDは管理向けにだけ現れる
                .body("tracks[0]", hasKey("trackId"))
                .body("externalAudios[0]", hasKey("externalAudioId"));
    }

    @Test
    @DisplayName("管理向け一覧は作品を選ぶための項目だけを返す")
    void adminListReturnsOnlySelectionFields() {
        final var albumId = createDraftAlbum("管理Query一覧項目");

        final var item = "items.find { it.albumId == '" + albumId + "' }";
        authorized().when().get("/api/v1/admin/albums?page=0&size=100").then().statusCode(200)
                .body(item + ".publishedAt", nullValue())
                .body(item, not(hasKey("tracks")))
                .body(item, not(hasKey("description")))
                .body(item, not(hasKey("externalAudios")))
                .body(item, not(hasKey("artistSortKey")));
    }

    @Test
    @DisplayName("存在しないIDの管理向け詳細は404 problem+jsonを返す")
    void adminDetailNotFound() {
        authorized().when().get("/api/v1/admin/albums/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    private static String createDraftAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"テストアーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }
}
