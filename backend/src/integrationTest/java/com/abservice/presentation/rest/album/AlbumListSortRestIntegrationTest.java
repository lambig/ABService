package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * アルバム一覧の並び順の統合テスト
 *
 * <p>
 * 並び順はクエリパラメータで選び、許可集合は要求元ごとに閉じる。既定（未指定）は登録の新しい順で、許可外の値は既定へ落とさず 400 を返す。
 * 各テストの前にデータベースを空へ戻すため（{@link CleanDatabase}）、母集団はそのテストが作ったものだけになる。並びは一覧の全体で確かめる。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("アルバム一覧の並び順の統合テスト")
class AlbumListSortRestIntegrationTest {

    @Test
    @DisplayName("リリース日の昇順・降順を指定すると指定どおりに並ぶ")
    void releaseDateOrderFollowsDirection() {
        final var oldest = createAlbum("1", "1901-01-01");
        final var middle = createAlbum("2", "1902-01-01");
        final var newest = createAlbum("3", "1903-01-01");

        assertThat(adminTitles("releaseDate", "asc"))
                .containsExactly(
                        oldest,
                        middle,
                        newest);
        assertThat(adminTitles("releaseDate", "desc"))
                .containsExactly(
                        newest,
                        middle,
                        oldest);
    }

    @Test
    @DisplayName("向きを省略するとキーごとの既定（リリース日は降順）になる")
    void releaseDateDefaultsToDescending() {
        final var older = createAlbum("1", "1911-01-01");
        final var newer = createAlbum("2", "1912-01-01");

        assertThat(adminTitlesSortedBy("releaseDate")).containsExactly(newer, older);
    }

    @Test
    @DisplayName("並び順を指定しなければ登録の新しい順になる")
    void unspecifiedSortReturnsNewestFirst() {
        final var firstCreated = createAlbum("1", "1921-01-01");
        final var lastCreated = createAlbum("2", "1922-01-01");

        assertThat(adminTitlesUnsorted()).containsExactly(lastCreated, firstCreated);
    }

    @Test
    @DisplayName("カタログナンバーでも並べられる")
    void catalogNumberIsUsableAsSortKey() {
        final var first = createAlbum("1", "1931-01-01");
        final var second = createAlbum("2", "1931-01-01");

        assertThat(adminTitles("catalogNumber", "asc")).containsExactly(first, second);
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

    private static String createAlbum(String suffix, String releaseDate) {
        final var title = "並び順テスト-%s".formatted(suffix);
        authorized().contentType(ContentType.JSON)
                .body(
                        albumBody(
                                title,
                                releaseDate,
                                suffix))
                .when().post("/api/v1/albums").then().statusCode(201);
        return title;
    }

    private static String albumBody(
            String title,
            String releaseDate,
            String suffix) {
        return ("{\"title\":\"%s\",\"releaseDate\":\"%s\",\"artistDisplayName\":\"並び順アーティスト\","
                + "\"catalogNumber\":\"SORT-%s\"}")
                .formatted(
                        title,
                        releaseDate,
                        suffix);
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
