package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * アルバム REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/albums}（作成）→ {@code GET /api/v1/albums/{id}}（詳細）→
 * {@code PUT /api/v1/albums/{id}}（更新）→ {@code POST
 * /api/v1/albums/{id}/publish}（公開）→ {@code POST
 * /api/v1/albums/{id}/unpublish}（非公開化）→ {@code DELETE
 * /api/v1/albums/{id}}（削除）の疎通と、 {@code GET /api/v1/albums}（一覧、ページネーション付き）、未存在時の
 * 404、検証エラー時の 400 を RFC 9457 Problem Details 込みで確認する。実 DB（Flyway
 * migrate-at-start）で動作する。GET 単体取得・一覧取得は認証を伴わない公開向けQueryのため、作成直後の下書き（未公開） アルバムは
 * 404／一覧除外となる（下書きを含めた閲覧は認証必須の別経路で提供予定、#116）。非公開化に伴う参照記事のカスケード
 * 非公開化は{@code UnpublishAlbumServiceIntegrationTest}で検証する（REST経由ではアルバム記事の作成手段が未提供のため）。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("アルバム REST エンドポイントの統合テスト")
class AlbumRestIntegrationTest {

    @Test
    @DisplayName("アルバムを作成すると下書き状態になり、公開向けAPIでのID詳細取得は404になる")
    void createThenGetIsNotFoundWhileUnpublished() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"E2Eテストアルバム\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"E2Eアーティスト\","
                                + "\"catalogNumber\":\"E2E-0001\",\"event\":{\"name\":\"コミックマーケット104\","
                                + "\"date\":\"2026-01-01\",\"place\":\"東京ビッグサイト\",\"spaceNumber\":\"東ホ-01a\"}}")
                .when().post("/api/v1/albums").then().statusCode(201).body("title", equalTo("E2Eテストアルバム"))
                .body("releaseDate", equalTo("2026-01-01")).body("artistDisplayName", equalTo("E2Eアーティスト")).extract()
                .path("albumId");

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("カバー画像のアセットキーを登録すると配信URLとして返る")
    void createWithCoverImageKeyReturnsDeliveryUrl() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"カバー画像付きアルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"E2Eアーティスト\","
                                + "\"coverImageKey\":\"01a0233d-d25a-7c3b-924f-236ee154fecc.png\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("coverImageUrl", equalTo("/assets/01a0233d-d25a-7c3b-924f-236ee154fecc.png"));
    }

    @Test
    @DisplayName("カバー画像のキーに配信URLを渡すと400 problem+jsonを返す")
    void createWithDeliveryUrlAsCoverImageKeyIsRejected() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"不正キーのアルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"E2Eアーティスト\","
                                + "\"coverImageKey\":\"/assets/01a0233d-d25a-7c3b-924f-236ee154fecc.png\"}")
                .when().post("/api/v1/albums").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].code", equalTo("ASSET_KEY_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("カバー画像なしのアルバムは配信URLがnullで返る")
    void albumWithoutCoverImageHasNullUrl() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"カバー画像なしアルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"E2Eアーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("coverImageUrl", nullValue());
    }

    @Test
    @DisplayName("存在しないIDは404 problem+jsonを返す")
    void getNotFound() {
        given().when().get("/api/v1/albums/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"))
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("タイトル空白は400 problem+json（検証エラー）を返す")
    void createValidationError() {
        authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"   \",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR")).body("status", equalTo(400))
                .body("errors", not(empty())).body("errors[0].field", equalTo("value"));
    }

    @Test
    @DisplayName("アルバムを更新すると全項目置換され、トラックは変化しない（下書きのままなので公開向けGETは404）")
    void updateReplacesFieldsAndPreservesTracks() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"更新前タイトル\",\"releaseDate\":\"2025-01-01\","
                                + "\"artistDisplayName\":\"更新前アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"更新後タイトル\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"更新後アーティスト\",\"catalogNumber\":\"UPD-0001\"}")
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200).body("albumId", equalTo(albumId))
                .body("title", equalTo("更新後タイトル")).body("releaseDate", equalTo("2026-01-01"))
                .body("artistDisplayName", equalTo("更新後アーティスト"));

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404);
    }

    @Test
    @DisplayName("更新はカバー画像・ISDN・イベントスペース番号も置換する")
    void updateReplacesCoverImageIsdnAndEventSpaceNumber() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"更新前タイトル\",\"releaseDate\":\"2025-01-01\","
                                + "\"artistDisplayName\":\"更新前アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"更新後タイトル\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"更新後アーティスト\",\"isdn\":\"2784702901978\","
                                + "\"coverImageKey\":\"01a0233d-d25a-7c3b-924f-236ee154fecc.png\","
                                + "\"event\":{\"name\":\"コミックマーケット104\",\"date\":\"2026-01-01\","
                                + "\"place\":\"東京ビッグサイト\",\"spaceNumber\":\"東ホ-01a\"}}")
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200);

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("isdn", equalTo("2784702901978"))
                .body("coverImageUrl", equalTo("/assets/01a0233d-d25a-7c3b-924f-236ee154fecc.png"))
                .body("eventSpaceNumber", equalTo("東ホ-01a"));
    }

    @Test
    @DisplayName("存在しないIDの更新は404 problem+jsonを返す")
    void updateNotFound() {
        authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"タイトル\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .put("/api/v1/albums/" + UUID.randomUUID()).then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("タイトル空白での更新は400 problem+json（検証エラー）を返す")
    void updateValidationError() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"タイトル\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"   \",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .put("/api/v1/albums/" + albumId).then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("value"));
    }

    @Test
    @DisplayName("アルバムを公開すると公開向けGETで参照できるようになる")
    void publishThenGetSucceeds() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"公開確認アルバム\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("published", equalTo(true));

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200).body("albumId", equalTo(albumId));
    }

    @Test
    @DisplayName("公開済みアルバムを非公開化すると公開向けGETは404になり、参照記事がなければカスケード対象も空")
    void unpublishThenGetNotFound() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"非公開化確認アルバム\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        authorized().when().post("/api/v1/albums/" + albumId + "/unpublish").then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("published", equalTo(false))
                .body("cascadeUnpublishedArticles", empty());

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404);
    }

    @Test
    @DisplayName("存在しないIDの公開は404 problem+jsonを返す")
    void publishNotFound() {
        authorized().when().post("/api/v1/albums/" + UUID.randomUUID() + "/publish").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("存在しないIDの非公開化は404 problem+jsonを返す")
    void unpublishNotFound() {
        authorized().when().post("/api/v1/albums/" + UUID.randomUUID() + "/unpublish").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("アルバムを削除すると以後のGETは404になる")
    void deleteThenGetNotFound() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"削除対象\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("affectedArticles", empty());

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404);
    }

    @Test
    @DisplayName("削除はべき等で、存在しないIDの削除も200を返す")
    void deleteIsIdempotent() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"べき等確認\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200);
        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(200);
        authorized().when().delete("/api/v1/albums/" + UUID.randomUUID()).then().statusCode(200);
    }

    @Test
    @DisplayName("一覧（公開向けAPI）は下書きアルバムを含まず、件数は作成しても増加しない")
    void listExcludesUnpublishedAlbums() {
        final int before = given().when().get("/api/v1/albums?page=0&size=1").then().statusCode(200).extract()
                .path("totalElements");

        final List<String> createdIds = List.of(
                createAlbum("一覧確認アルバム1"),
                createAlbum("一覧確認アルバム2"),
                createAlbum("一覧確認アルバム3"));

        final var response = given().when().get("/api/v1/albums?page=0&size=100").then().statusCode(200)
                .body("totalElements", equalTo(before)).extract();

        final List<String> ids = response.path("items.albumId");
        assertThat(ids).doesNotContainAnyElementsOf(createdIds);
    }

    @Test
    @DisplayName("公開向け詳細は曲目をチューン構成つきで返す")
    void publicDetailReturnsTracksWithTunes() {
        final String albumId = createAlbum("曲目確認アルバム");
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"trackNo\":1,\"title\":\"1曲目\",\"artistDisplayName\":\"トラックアーティスト\","
                                + "\"tunes\":["
                                + "{\"seq\":1,\"tuneTitle\":\"チューン1\",\"composerCreditOverride\":\"Trad.\"},"
                                + "{\"seq\":2,\"tuneTitle\":\"チューン2\"}]}")
                .when().post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(201);
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("tracks[0].trackNo", equalTo(1)).body("tracks[0].title", equalTo("1曲目"))
                .body("tracks[0].artistDisplayName", equalTo("トラックアーティスト"))
                .body("tracks[0].tunes[0].seq", equalTo(1))
                .body("tracks[0].tunes[0].tuneTitle", equalTo("チューン1"))
                .body("tracks[0].tunes[0].composerCreditOverride", equalTo("Trad."))
                .body("tracks[0].tunes[1].tuneTitle", equalTo("チューン2"))
                .body("tracks[0].tunes[1].composerCreditOverride", nullValue())
                // ソートキーは編集のための値、トラックIDは編集対象を同定するための値のため、公開向けには現れない
                .body("tracks[0]", not(hasKey("artistSortKey")))
                .body("tracks[0]", not(hasKey("trackId")));
    }

    @Test
    @DisplayName("公開向け一覧は作品を選ぶための項目だけを返す")
    void publicListReturnsOnlySelectionFields() {
        final String albumId = createAlbum("一覧項目確認アルバム");
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        final String item = "items.find { it.albumId == '" + albumId + "' }";
        given().when().get("/api/v1/albums?page=0&size=100").then().statusCode(200)
                .body(item + ".title", equalTo("一覧項目確認アルバム"))
                // 曲目・概要説明・外部音源は詳細で返す。概要説明は長さに制限がなくカードが崩れる
                .body(item, not(hasKey("tracks")))
                .body(item, not(hasKey("description")))
                .body(item, not(hasKey("descriptionFormat")))
                .body(item, not(hasKey("externalAudios")))
                // ソートキーは編集のための値で、公開サイトは表示にも並びにも使わない
                .body(item, not(hasKey("artistSortKey")));
    }

    @Test
    @DisplayName("公開向け詳細は概要説明と外部音源を返し、編集のための項目名は返さない")
    void publicDetailOmitsEditingOnlyKeys() {
        final String albumId = createAlbum("詳細項目確認アルバム");
        authorized().contentType(ContentType.JSON)
                .body("{\"url\":\"https://soundcloud.com/example/detail-key-check\"}")
                .when().post("/api/v1/albums/" + albumId + "/external-audios").then().statusCode(201);
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("$", hasKey("description"))
                .body("$", hasKey("descriptionFormat"))
                .body("$", hasKey("externalAudios"))
                .body("$", not(hasKey("artistSortKey")))
                .body("externalAudios[0].url", equalTo("https://soundcloud.com/example/detail-key-check"))
                // 外部音源IDは管理画面が削除・並び替えの対象を同定するための値のため、公開向けには現れない
                .body("externalAudios[0]", not(hasKey("externalAudioId")))
                .body("publishedAt", notNullValue());
    }

    @Test
    @DisplayName("一覧のpage/sizeを省略するとデフォルト値（page=0, size=20）が使われる")
    void listUsesDefaultPageAndSizeWhenOmitted() {
        given().when().get("/api/v1/albums").then().statusCode(200).body("page", equalTo(0))
                .body("size", equalTo(20));
    }

    @Test
    @DisplayName("管理向け一覧をタイトルで絞り込める（部分一致・大文字小文字を問わない）")
    void adminListFiltersByTitle() {
        final String marker = marker();
        final String targetId = createAlbum("Filter" + marker + "Target");
        final String otherId = createAlbum("Filter" + marker + "Other");

        authorized().queryParam("size", 100).queryParam("title", "FILTER" + marker + "TARGET")
                .when().get("/api/v1/admin/albums").then().statusCode(200)
                .body("items.albumId", hasItem(targetId))
                .body("items.albumId", not(hasItem(otherId)));
    }

    @Test
    @DisplayName("管理向け一覧をカタログナンバーで絞り込め、タイトルと併せると積で絞り込まれる")
    void adminListFiltersByCatalogNumber() {
        final String marker = marker();
        final String targetId = createAlbumWithCatalogNumber("Catalog" + marker + "Target", "CAT-" + marker);
        final String otherId = createAlbumWithCatalogNumber("Catalog" + marker + "Other", "OTHER-" + marker);

        authorized().queryParam("size", 100).queryParam("catalogNumber", "cat-" + marker)
                .when().get("/api/v1/admin/albums").then().statusCode(200)
                .body("items.albumId", hasItem(targetId))
                .body("items.albumId", not(hasItem(otherId)));

        // タイトルは両方に当たるが、カタログナンバーとの積で1件へ絞られる
        authorized().queryParam("size", 100).queryParam("title", "Catalog" + marker)
                .queryParam("catalogNumber", "cat-" + marker)
                .when().get("/api/v1/admin/albums").then().statusCode(200)
                .body("items.albumId", hasItem(targetId))
                .body("items.albumId", not(hasItem(otherId)));
    }

    @Test
    @DisplayName("空文字・空白のみの検索パラメータは未指定として扱われ、カタログナンバーを持たないアルバムも落ちない")
    void adminListTreatsBlankSearchParametersAsUnspecified() {
        final String marker = marker();
        final String withoutCatalog = createAlbum("Blank" + marker + "NoCatalog");
        final String withCatalog = createAlbumWithCatalogNumber("Blank" + marker + "WithCatalog", "CAT-" + marker);

        // catalogNumber が空文字だと like '%%' が積に加わり、列が null の行だけが落ちる
        authorized().queryParam("size", 100).queryParam("title", "Blank" + marker)
                .queryParam("catalogNumber", "")
                .when().get("/api/v1/admin/albums").then().statusCode(200)
                .body("items.albumId", hasItem(withoutCatalog))
                .body("items.albumId", hasItem(withCatalog));

        authorized().queryParam("size", 100).queryParam("title", "Blank" + marker)
                .queryParam("catalogNumber", "   ")
                .when().get("/api/v1/admin/albums").then().statusCode(200)
                .body("items.albumId", hasItem(withoutCatalog))
                .body("items.albumId", hasItem(withCatalog));
    }

    @Test
    @DisplayName("公開向け一覧は検索パラメータを受け取らず、絞り込まれない")
    void publicListIgnoresSearchParameters() {
        final String marker = marker();
        final String targetId = createAlbum("Public" + marker + "Target");
        final String otherId = createAlbum("Public" + marker + "Other");
        authorized().when().post("/api/v1/albums/" + targetId + "/publish").then().statusCode(200);
        authorized().when().post("/api/v1/albums/" + otherId + "/publish").then().statusCode(200);

        given().queryParam("size", 100).queryParam("title", "Public" + marker + "Target")
                .when().get("/api/v1/albums").then().statusCode(200)
                .body("items.albumId", hasItem(targetId))
                .body("items.albumId", hasItem(otherId));
    }

    /** テスト間・実行間でタイトルとカタログナンバーが衝突しないようにする識別子 */
    private static String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String createAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static String createAlbumWithCatalogNumber(String title, String catalogNumber) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\",\"catalogNumber\":\"" + catalogNumber + "\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }
}
