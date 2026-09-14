package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.hamcrest.Matchers.equalTo;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 作品の子をIDで指す要求の境界（#391）の統合テスト
 *
 * <p>
 * 「IDあり＝既存、IDなし＝新規」という契約は、<b>そのIDがこの作品の子であること</b>まで確かめて初めて成り立つ。
 * 確かめなければ、要求元が選んだ識別子を持つ子を作れてしまう（永続化は、知らないIDを新しい行として受け取る）。
 * 識別は親の中でしか意味を持たないため、判定できるのは集約だけである。
 * </p>
 *
 * <p>
 * 同じIDを2度指す要求も拒む。永続化では1行に畳まれ、要求は2行なのに結果は1行という食い違いになる。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("作品の子をIDで指す要求の境界の統合テスト")
class AlbumChildOwnershipRestIntegrationTest {

    private static final String CONFLICT_TYPE = "urn:abservice:error:BUSINESS_RULE_VIOLATION";
    private static final String FIRST_AUDIO_URL = "https://soundcloud.com/example/ownership-first";

    /** 曲目と外部音源を1件ずつ持つ作品を作る */
    private static String createAlbumWithChildren(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"境界テストアーティスト\","
                                + "\"tracks\":[{\"title\":\"1曲目\"}],"
                                + "\"externalAudios\":[{\"url\":\"" + FIRST_AUDIO_URL + "\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(201)
                .extract().path("albumId");
    }

    private static int revisionOf(String albumId) {
        return authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .extract().path("revision");
    }

    private static String trackIdOf(String albumId) {
        return authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .extract().path("tracks[0].trackId");
    }

    private static String externalAudioIdOf(String albumId) {
        return authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .extract().path("externalAudios[0].externalAudioId");
    }

    /** 子だけを差し替える更新の本体。本体の項目は通る値で固める */
    private static String updateWith(
            String title,
            int expectedRevision,
            String children) {
        return "{\"expectedRevision\":" + expectedRevision + ",\"title\":\"" + title + "\","
                + "\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"境界テストアーティスト\","
                + children + "}";
    }

    private static String tracks(String rows) {
        return "\"tracks\":" + rows;
    }

    private static String externalAudios(String rows) {
        return "\"externalAudios\":" + rows;
    }

    private static void expectRejected(String albumId, String body) {
        authorized().contentType(ContentType.JSON).body(body)
                .when().put("/api/v1/albums/" + albumId).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo(CONFLICT_TYPE));
    }

    @Test
    @DisplayName("この作品が持たないトラックIDを送ると409になる")
    void unknownTrackIdIsRejected() {
        final String albumId = createAlbumWithChildren("未知のトラックID");

        expectRejected(
                albumId,
                updateWith(
                        "未知のトラックID",
                        revisionOf(albumId),
                        tracks("[{\"trackId\":\"" + UUID.randomUUID() + "\",\"title\":\"1曲目\"}]")));
    }

    @Test
    @DisplayName("別の作品が持つトラックIDを送っても409になる")
    void trackIdOfAnotherAlbumIsRejected() {
        final String other = createAlbumWithChildren("よその作品");
        final String albumId = createAlbumWithChildren("よそのトラックID");

        expectRejected(
                albumId,
                updateWith(
                        "よそのトラックID",
                        revisionOf(albumId),
                        tracks("[{\"trackId\":\"" + trackIdOf(other) + "\",\"title\":\"1曲目\"}]")));
    }

    @Test
    @DisplayName("同じトラックIDを2度送ると409になる")
    void duplicatedTrackIdIsRejected() {
        final String albumId = createAlbumWithChildren("重複するトラックID");
        final String trackId = trackIdOf(albumId);

        expectRejected(
                albumId,
                updateWith(
                        "重複するトラックID",
                        revisionOf(albumId),
                        tracks(
                                "[{\"trackId\":\"" + trackId + "\",\"title\":\"1度目\"},"
                                        + "{\"trackId\":\"" + trackId + "\",\"title\":\"2度目\"}]")));
    }

    @Test
    @DisplayName("この作品が持たない外部音源IDを送ると409になる")
    void unknownExternalAudioIdIsRejected() {
        final String albumId = createAlbumWithChildren("未知の音源ID");

        expectRejected(
                albumId,
                updateWith(
                        "未知の音源ID",
                        revisionOf(albumId),
                        externalAudios(
                                "[{\"externalAudioId\":\"" + UUID.randomUUID() + "\",\"url\":\""
                                        + FIRST_AUDIO_URL + "\"}]")));
    }

    @Test
    @DisplayName("別の作品が持つ外部音源IDを送っても409になる")
    void externalAudioIdOfAnotherAlbumIsRejected() {
        final String other = createAlbumWithChildren("よその作品の音源");
        final String albumId = createAlbumWithChildren("よその音源ID");

        expectRejected(
                albumId,
                updateWith(
                        "よその音源ID",
                        revisionOf(albumId),
                        externalAudios(
                                "[{\"externalAudioId\":\"" + externalAudioIdOf(other) + "\",\"url\":\""
                                        + FIRST_AUDIO_URL + "\"}]")));
    }

    @Test
    @DisplayName("まだ子を持たない作品の作成で、子のIDを指定すると409になる")
    void claimedIdsAreRejectedOnCreation() {
        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"作成時のID指定\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"境界テストアーティスト\","
                                + "\"tracks\":[{\"trackId\":\"" + UUID.randomUUID()
                                + "\",\"title\":\"1曲目\"}]}")
                .when().post("/api/v1/albums/with-tracks").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo(CONFLICT_TYPE));
    }

    @Test
    @DisplayName("自分の子のIDなら、組み直して保存できる")
    void ownedIdsAreAccepted() {
        final String albumId = createAlbumWithChildren("自分の子のID");

        authorized().contentType(ContentType.JSON)
                .body(
                        updateWith(
                                "自分の子のID",
                                revisionOf(albumId),
                                tracks(
                                        "[{\"trackId\":\"" + trackIdOf(albumId)
                                                + "\",\"title\":\"組み直した1曲目\"}]")))
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200);

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("tracks[0].title", equalTo("組み直した1曲目"))
                .body("tracks[0].trackNo", equalTo(1));
    }
}
