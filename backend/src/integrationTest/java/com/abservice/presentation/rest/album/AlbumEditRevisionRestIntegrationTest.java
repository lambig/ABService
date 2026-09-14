package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 編集開始時点を更新の条件にする契約（#287）の E2E 統合テスト
 *
 * <p>
 * 全項目置換の更新は、届いた値を最新の状態へ適用する。読んだ時点を条件として持ち込まない限り、2つのタブが同じ詳細を
 * 読んでから順に保存したとき、後の保存が前の保存を消す。DBの版でも行ロックでもこれは検出できない（後の要求は最新を
 * 読み直してから更新するため、競合が起きていない）。ここで見ているのは、その古いフォームを拒めることである。
 * </p>
 *
 * <p>
 * 世代を進めるのは作品本体の変更だけである（トラック等の子は別の編集単位）。その境界もここで固定する。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("編集開始時点を更新条件にする契約の統合テスト")
class AlbumEditRevisionRestIntegrationTest {

    private static String createAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"世代テストアーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    /** 管理詳細が返す編集の世代。画面はこれをそのまま更新へ返す */
    private static int revisionOf(String albumId) {
        return authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200).extract()
                .path("revision");
    }

    private static String updateBody(int expectedRevision, String title) {
        return "{\"expectedRevision\":" + expectedRevision + ",\"title\":\"" + title + "\","
                + "\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"世代テストアーティスト\"}";
    }

    @Test
    @DisplayName("2つのタブが同じ詳細を読んだ後、後から届いた古いフォームは409になり、先の保存が残る")
    void staleFormIsRejectedAndTheFirstSaveSurvives() {
        final String albumId = createAlbum("世代テストアルバム");

        /* タブA・タブBが同じ詳細を読む（同じ世代を持つ） */
        final int revisionInTabA = revisionOf(albumId);
        final int revisionInTabB = revisionOf(albumId);

        authorized().contentType(ContentType.JSON).body(updateBody(revisionInTabA, "タブAの保存"))
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200);

        /*
         * タブBは別の項目だけを直したつもりで、読んだ時点のタイトルを含む全項目を送る。世代の条件が無ければ、 これがタブAの保存を消す。
         */
        authorized().contentType(ContentType.JSON).body(updateBody(revisionInTabB, "タブBの保存"))
                .when().put("/api/v1/albums/" + albumId).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:CONFLICTING_UPDATE"));

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("title", equalTo("タブAの保存"));
    }

    @Test
    @DisplayName("最新の世代なら保存でき、応答と詳細の世代が進む")
    void latestRevisionSavesAndAdvancesTheRevision() {
        final String albumId = createAlbum("世代前進アルバム");
        final int revision = revisionOf(albumId);

        final int savedRevision = authorized().contentType(ContentType.JSON)
                .body(updateBody(revision, "世代前進アルバム（改題）"))
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200).extract().path("revision");

        assertThat(savedRevision).as("保存で世代が進む").isGreaterThan(revision);
        assertThat(revisionOf(albumId)).as("詳細も進んだ世代を返す")
                .isEqualTo(savedRevision);

        /* 返った世代で続けて保存できる（読み直しを要さない） */
        authorized().contentType(ContentType.JSON).body(updateBody(savedRevision, "世代前進アルバム（再改題）"))
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200);
    }

    @Test
    @DisplayName("世代を伴わない更新は400で、位置は expectedRevision になる")
    void updateWithoutExpectedRevisionIsRejected() {
        final String albumId = createAlbum("世代なし更新アルバム");

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"世代なしの保存\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"世代テストアーティスト\"}")
                .when().put("/api/v1/albums/" + albumId).then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("expectedRevision"))
                .body("errors[0].code", equalTo("ALBUM_EXPECTED_REVISION_REQUIRED"));

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("title", equalTo("世代なし更新アルバム"));
    }

    @Test
    @DisplayName("曲目だけを変えた保存も作品の更新として世代を進める")
    void changingOnlyTracksAdvancesTheAlbumRevision() {
        final String albumId = createAlbum("曲目だけの保存アルバム");
        final int revision = revisionOf(albumId);

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"expectedRevision\":" + revision + ",\"title\":\"曲目だけの保存アルバム\","
                                + "\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"世代テストアーティスト\","
                                + "\"tracks\":[{\"title\":\"1曲目\"}]}")
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200);

        assertThat(revisionOf(albumId))
                .as("保存の口が集約ルートに1つである以上、曲目だけの変更も作品の更新である").isEqualTo(revision + 1);

        /*
         * STALE-AFTER-CHILD-SAVE: 進めないと、2つのタブが別々のトラックを直した保存が競合にならず、後の保存が
         * 前の変更を全項目置換で消す。古い世代を条件にした保存が断られることで、その経路が塞がれている。
         */
        authorized().contentType(ContentType.JSON).body(updateBody(revision, "曲目だけの保存アルバム（改題）"))
                .when().put("/api/v1/albums/" + albumId).then().statusCode(409);
    }
}
