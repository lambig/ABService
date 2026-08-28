package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバム一覧の並び順の統合テスト
 *
 * <p>
 * 並び順はクエリパラメータで選び、許可集合は要求元ごとに閉じる。既定（未指定）は登録の新しい順で、許可外の値は既定へ落とさず 400 を返す。
 * 実DBは他のテストが作ったアルバムも含むため、順序の検証は本テストが作った数件の相対順で行う。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバム一覧の並び順の統合テスト")
class AlbumListSortRestIntegrationTest {

    @Test
    @DisplayName("リリース日の昇順・降順を指定すると指定どおりに並ぶ")
    void releaseDateOrderFollowsDirection() {
        final var marker = UUID.randomUUID().toString();
        final var oldest = createAlbum(label(marker, "1"), "1901-01-01");
        final var middle = createAlbum(label(marker, "2"), "1902-01-01");
        final var newest = createAlbum(label(marker, "3"), "1903-01-01");

        final var ascending = adminTitles("releaseDate", "asc");
        assertThat(ascending.indexOf(oldest)).isLessThan(ascending.indexOf(middle));
        assertThat(ascending.indexOf(middle)).isLessThan(ascending.indexOf(newest));

        final var descending = adminTitles("releaseDate", "desc");
        assertThat(descending.indexOf(newest)).isLessThan(descending.indexOf(middle));
        assertThat(descending.indexOf(middle)).isLessThan(descending.indexOf(oldest));
    }

    @Test
    @DisplayName("向きを省略するとキーごとの既定（リリース日は降順）になる")
    void releaseDateDefaultsToDescending() {
        final var marker = UUID.randomUUID().toString();
        final var older = createAlbum(label(marker, "1"), "1911-01-01");
        final var newer = createAlbum(label(marker, "2"), "1912-01-01");

        final var titles = adminTitlesSortedBy("releaseDate");

        assertThat(titles.indexOf(newer)).isLessThan(titles.indexOf(older));
    }

    @Test
    @DisplayName("並び順を指定しなければ登録の新しい順になる")
    void unspecifiedSortReturnsNewestFirst() {
        final var marker = UUID.randomUUID().toString();
        createAlbum(label(marker, "1"), "1921-01-01");
        final var lastCreated = createAlbum(label(marker, "2"), "1922-01-01");

        final var titles = adminTitlesUnsorted();

        assertThat(titles).first().isEqualTo(lastCreated);
    }

    @Test
    @DisplayName("カタログナンバーでも並べられる")
    void catalogNumberIsUsableAsSortKey() {
        final var marker = UUID.randomUUID().toString();
        final var first = createAlbum(label(marker, "1"), "1931-01-01");
        final var second = createAlbum(label(marker, "2"), "1931-01-01");

        final var titles = adminTitles("catalogNumber", "asc");

        assertThat(titles.indexOf(first)).isLessThan(titles.indexOf(second));
    }

    @Test
    @DisplayName("監査列のキーは公開向けAPIでは使えない")
    void auditColumnKeyIsRejectedOnPublicApi() {
        given().queryParam("sort", "updatedAt").when().get("/api/v1/albums").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("sort"))
                .body("errors[0].code", equalTo("SORT_KEY_NOT_USABLE"));
    }

    /*
     * 監査列は記録のための列であり、業務文脈の並び順には使わない（#197）。管理向けにも開けないことを固定する。
     */
    @Test
    @DisplayName("監査列のキーは管理向けAPIでも使えない")
    void auditColumnKeyIsRejectedOnAdminApi() {
        authorized().queryParam("sort", "updatedAt").when().get("/api/v1/admin/albums").then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].field", equalTo("sort"))
                .body("errors[0].code", equalTo("SORT_KEY_NOT_USABLE"));

        authorized().queryParam("sort", "createdAt").when().get("/api/v1/admin/albums").then().statusCode(400)
                .body("errors[0].code", equalTo("SORT_KEY_NOT_USABLE"));
    }

    @Test
    @DisplayName("未知のキーは400 problem+jsonを返し、既定へ落とさない")
    void unknownKeyIsRejected() {
        given().queryParam("sort", "title").when().get("/api/v1/albums").then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].code", equalTo("SORT_KEY_NOT_USABLE"));
    }

    @Test
    @DisplayName("未知の向きは400 problem+jsonを返す")
    void unknownDirectionIsRejected() {
        given().queryParam("sort", "releaseDate").queryParam("direction", "sideways").when().get("/api/v1/albums")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].field", equalTo("direction"))
                .body("errors[0].code", equalTo("SORT_DIRECTION_NOT_USABLE"));
    }

    private static String label(String marker, String suffix) {
        return "%s-%s".formatted(marker, suffix);
    }

    private static String createAlbum(String label, String releaseDate) {
        final var title = "並び順テスト-%s".formatted(label);
        authorized().contentType(ContentType.JSON)
                .body(
                        albumBody(
                                title,
                                releaseDate,
                                label))
                .when().post("/api/v1/albums").then().statusCode(201);
        return title;
    }

    private static String albumBody(
            String title,
            String releaseDate,
            String label) {
        return ("{\"title\":\"%s\",\"releaseDate\":\"%s\",\"artistDisplayName\":\"並び順アーティスト\","
                + "\"catalogNumber\":\"SORT-%s\"}")
                .formatted(
                        title,
                        releaseDate,
                        label);
    }

    private static List<String> adminTitles(String sort, String direction) {
        return titlesOf(adminList().queryParam("sort", sort).queryParam("direction", direction));
    }

    private static List<String> adminTitlesSortedBy(String sort) {
        return titlesOf(adminList().queryParam("sort", sort));
    }

    private static List<String> adminTitlesUnsorted() {
        return titlesOf(adminList());
    }

    private static RequestSpecification adminList() {
        return authorized().queryParam("size", 100);
    }

    private static List<String> titlesOf(RequestSpecification request) {
        return request.when().get("/api/v1/admin/albums").then().statusCode(200)
                .extract().path("items.title");
    }
}
