package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.common.http.TestHTTPResource;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.security.DigestInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.Map;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;

@QuarkusTest
@TestProfile(PrivateAudioUploadRestIntegrationTest.AudioRuntime.class)
@QuarkusTestResource(value = AudioHttpTestResource.class, restrictToAnnotatedClass = true)
@ExtendWith(CleanDatabase.class)
class PrivateAudioUploadRestIntegrationTest {
    /** 専用リソースでの再起動を通常テストと分離し、前の起動のReactiveセッション状態を引き継がない。 */
    public static class AudioRuntime implements QuarkusTestProfile {
    }

    private static final String BASE = "/api/v1/admin/private-audio/registrations";
    @Inject
    private S3Client storage;
    @TempDir
    private Path directory;
    @TestHTTPResource
    private URI endpoint;

    @Test
    @DisplayName("実FLACの予約からAlbum関連付けまで通しても公開情報と画像保存先へ音源が漏れない")
    void registersAndAssociatesWithoutPublishingAudio() throws Exception {
        final String album = authorized().contentType("application/json").body(
                Map.of(
                        "title",
                        "Synthetic crossfade acceptance",
                        "releaseDate",
                        "2026-01-01",
                        "artistDisplayName",
                        "Fixture"))
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
        authorized().post("/api/v1/albums/" + album + "/publish").then().statusCode(200);
        final String before = given().get("/api/v1/albums/" + album).then().statusCode(200).extract().asString();
        final Object generation = given().get("/api/v1/public-data-generation").then().statusCode(200)
                .extract().path("generation");
        final var id = reserve();
        final var selection = Map.of(
                "audioId",
                id,
                "expectedRevision",
                0);
        final var crossfade = "/api/v1/admin/albums/" + album + "/listening-audio/crossfade";
        authorized().contentType("application/json").body(selection).put(crossfade).then().statusCode(409);
        final var bytes = fixture(4096);
        authorized().contentType("audio/flac").body(bytes).put(content(id)).then().statusCode(200)
                .body("state", equalTo("CONFIRMED"));
        authorized().contentType("application/json").body(selection).put(crossfade).then().statusCode(200)
                .body("audioId", equalTo(id)).body("kind", equalTo("album-crossfade")).body("revision", equalTo(1));
        authorized().get(crossfade).then().statusCode(200).body("audioId", equalTo(id));
        assertThat(given().get("/api/v1/albums/" + album).then().statusCode(200).extract().asString())
                .isEqualTo(before);
        given().get("/api/v1/public-data-generation").then().statusCode(200).body("generation", equalTo(generation));
        given().get("/api/v1/private-audio/registrations/" + id).then().statusCode(404);
        given().get("/api/v1/albums/" + album + "/listening-audio/crossfade").then().statusCode(404);
        given().get("http://localhost:9000/" + bucket() + "/" + key(id)).then().statusCode(403);
        final var images = ConfigProvider.getConfig().getValue("abservice.assets.bucket", String.class);
        assertThat(images).isNotEqualTo(bucket());
        assertThat(storage.listObjectsV2(request -> request.bucket(images).prefix(key(id))).keyCount()).isZero();
        assertThat(storage.listObjectsV2(request -> request.bucket(images).prefix("assets/" + id)).keyCount()).isZero();
        authorized().delete("/api/v1/albums/" + album).then().statusCode(200);
        authorized().get(crossfade).then().statusCode(404);
        authorized().get(BASE + "/" + id).then().statusCode(200).body("state", equalTo("CONFIRMED"));
        assertThat(storage.getObjectAsBytes(request -> request.bucket(bucket()).key(key(id))).asByteArray())
                .isEqualTo(bytes);
    }

    @Test
    @DisplayName("検査後に別の正常FLACへ差し替えられた実体を復旧APIが確定せず上書きもしない")
    void refusesReplacedObjectDuringRecovery() throws Exception {
        final var first = reserve();
        authorized().contentType("audio/flac").body(fixture(4096)).put(content(first)).then().statusCode(200);
        final var second = reserve();
        final var replacement = fixture(8192);
        authorized().contentType("audio/flac").body(replacement).put(content(second)).then().statusCode(200);
        // 故障注入: 管理APIでは許さない保存先の差替えを、テスト所有バケットへ直接行う。
        storage.copyObject(
                request -> request.copySource(bucket() + "/" + key(second))
                        .destinationBucket(bucket()).destinationKey(key(first))
                        .checksumAlgorithm(ChecksumAlgorithm.SHA256));
        update(first, "state = 'INSPECTED'");
        authorized().post(BASE + "/" + first + "/confirm").then().statusCode(409);
        authorized().get(BASE + "/" + first).then().statusCode(200).body("state", equalTo("INSPECTED"));
        assertThat(storage.getObjectAsBytes(request -> request.bucket(bucket()).key(key(first))).asByteArray())
                .isEqualTo(replacement);
    }

    @Test
    @DisplayName("音源機能を有効にしても画像の署名付き登録・公開とFLAC形式の拒否を維持する")
    void keepsImageUploadSeparate() {
        authorized().contentType("application/json").body(Map.of("contentType", "audio/flac"))
                .post("/api/v1/assets/upload-url").then().statusCode(400);
        final var issued = authorized().contentType("application/json").body(Map.of("contentType", "image/png"))
                .post("/api/v1/assets/upload-url").then().statusCode(200).body("maxBytes", equalTo(1024)).extract();
        final String assetKey = issued.path("assetKey");
        final String uploadUrl = issued.path("uploadUrl");
        final var png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC");
        given().config(
                RestAssured.config().encoderConfig(
                        EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .urlEncodingEnabled(false).contentType("image/png").body(png).put(uploadUrl).then().statusCode(200);
        authorized().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(200)
                .body("url", equalTo("/assets/" + assetKey)).body("sizeBytes", equalTo(png.length));
        final var images = ConfigProvider.getConfig().getValue("abservice.assets.bucket", String.class);
        try {
            assertThat(
                    given().get("http://localhost:9000/" + images + "/assets/" + assetKey)
                            .then().statusCode(200).extract().asByteArray())
                    .isEqualTo(png);
            assertThat(
                    storage.listObjectsV2(request -> request.bucket(bucket()).prefix("assets/" + assetKey)).keyCount())
                    .isZero();
        } finally {
            storage.deleteObject(request -> request.bucket(images).key("assets/" + assetKey));
        }
    }

    @Test
    @DisplayName("10MiBを超える実FLACをHTTPから検査・非公開保存・確定し再送で上書きしない")
    void receivesLargeFlacAndConfirms() throws Exception {
        final var file = fixtureFile(12 * 1024 * 1024);
        assertThat(Files.size(file)).isGreaterThan(10 * 1024 * 1024);
        final var id = reserve();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            final var request = HttpRequest.newBuilder(endpoint.resolve(content(id)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer test-admin-api-key").header("Content-Type", "audio/flac")
                    .PUT(HttpRequest.BodyPublishers.ofFile(file)).build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
        authorized().get(BASE + "/" + id).then().statusCode(200)
                .body("state", equalTo("CONFIRMED"))
                .body("metadata.byteLength", equalTo((int) Files.size(file)))
                .body("metadata.sha256", equalTo(sha256(file)))
                .body("$", not(hasKey("url"))).body("$", not(hasKey("storageKey")));
        assertStoredFile(id, file);
        given().get("http://localhost:9000/" + bucket() + "/" + key(id)).then().statusCode(403);
        authorized().post(BASE + "/" + id + "/confirm").then().statusCode(200).body("state", equalTo("CONFIRMED"));
        try (var socket = openUpload(id, Files.size(file))) {
            assertThat(status(socket)).contains("409");
        }
        assertStoredFile(id, file);
    }

    private void assertStoredFile(String id, Path expected) throws Exception {
        final var downloaded = directory.resolve(UUID.randomUUID() + ".download");
        storage.getObject(request -> request.bucket(bucket()).key(key(id)), downloaded);
        assertThat(Files.mismatch(expected, downloaded)).isEqualTo(-1);
    }

    private static String sha256(Path file) throws Exception {
        final var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Test
    @DisplayName("認証・不正ID・未存在・期限切れ・不正FLACを受信前後の適切な境界で拒否する")
    void rejectsInvalidRequests() throws Exception {
        final var id = reserve();
        given().contentType("audio/flac").body(new byte[]{102, 76, 97, 67}).put(content(id)).then().statusCode(401);
        given().post(BASE + "/" + id + "/confirm").then().statusCode(401);
        authorized().contentType("audio/flac").body(new byte[]{102, 76, 97, 67}).put(content("invalid")).then()
                .statusCode(400);
        authorized().contentType("audio/flac").body(new byte[]{102, 76, 97, 67})
                .put(content(UUID.randomUUID().toString())).then()
                .statusCode(404);
        authorized().post(BASE + "/" + id + "/confirm").then().statusCode(409);
        authorized().contentType("audio/flac").body(new byte[]{102, 76, 97, 67}).put(content(id)).then()
                .statusCode(400);
        authorized().get(BASE + "/" + id).then().statusCode(200).body("state", equalTo("PENDING"));
        assertThat(storage.listObjectsV2(request -> request.bucket(bucket()).prefix(key(id))).keyCount()).isZero();
        update(
                id,
                "created_at = clock_timestamp() - interval '2 hours', "
                        + "expires_at = clock_timestamp() - interval '1 hour'");
        authorized().contentType("audio/flac").body(fixture(4096)).put(content(id)).then().statusCode(409);
    }

    @Test
    @DisplayName("検査済み・放棄済みを保存実体と照合して復旧し未存在の実体は確定しない")
    void recoversOnlyMatchingObjects() throws Exception {
        final var id = reserve();
        authorized().contentType("audio/flac").body(fixture(4096)).put(content(id)).then().statusCode(200);
        update(id, "state = 'INSPECTED'");
        authorized().post(BASE + "/" + id + "/confirm").then().statusCode(200).body("state", equalTo("CONFIRMED"));
        update(id, "state = 'ABANDONED'");
        authorized().post(BASE + "/" + id + "/confirm").then().statusCode(200).body("state", equalTo("CONFIRMED"));
        update(id, "state = 'INSPECTED'");
        storage.deleteObject(request -> request.bucket(bucket()).key(key(id)));
        authorized().post(BASE + "/" + id + "/confirm").then().statusCode(409);
        authorized().get(BASE + "/" + id).then().statusCode(200).body("state", equalTo("INSPECTED"));
    }

    @Test
    @DisplayName("音源以外のJSON受信上限は10MiBのまま維持する")
    void keepsStandardBodyLimit() throws Exception {
        try (var socket = openRequest(
                "POST",
                "/api/v1/albums",
                11L * 1024 * 1024,
                "application/json")) {
            assertThat(status(socket)).contains("413");
        }
    }

    @Test
    @DisplayName("chunked入力も同じFLAC検査を通り通常APIのchunked上限は維持する")
    void acceptsChunkedInput() throws Exception {
        final var id = reserve();
        final var bytes = fixture(4096);
        final var oversized = directory.resolve("oversized.json");
        try (var writer = Files.newBufferedWriter(oversized)) {
            writer.write("{\"title\":\"");
            for (int block = 0; block < 11 * 1024; block++) {
                writer.write("a".repeat(1024));
            }
            writer.write("\"}");
        }
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            final var request = HttpRequest.newBuilder(endpoint.resolve(content(id)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer test-admin-api-key").header("Content-Type", "audio/flac")
                    .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))).build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            final var tooLarge = HttpRequest.newBuilder(endpoint.resolve("/api/v1/albums"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer test-admin-api-key").header("Content-Type", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers
                                    .ofInputStream(() -> {
                                        try {
                                            return Files.newInputStream(oversized);
                                        } catch (IOException failure) {
                                            throw new UncheckedIOException(failure);
                                        }
                                    }))
                    .build();
            assertThat(client.send(tooLarge, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
        }
    }

    @Test
    @DisplayName("上限を超えたContent-Lengthは本文を待たずに拒否する")
    void rejectsOversizeBeforeReading() throws Exception {
        try (var socket = openUpload(reserve(), 256L * 1024 * 1024 + 1)) {
            assertThat(status(socket)).contains("413");
        }
    }

    @Test
    @DisplayName("途中停止した送信を期限で打ち切り次の受信に資源を戻す")
    void releasesAfterStalledSender() throws Exception {
        final var id = reserve();
        try (var socket = openUpload(id, 8192)) {
            socket.getOutputStream().write("fLaC".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            assertThat(status(socket)).contains("500");
        }
        authorized().get(BASE + "/" + id).then().statusCode(200).body("state", equalTo("PENDING"));
        authorized().contentType("audio/flac").body(fixture(4096)).put(content(id)).then().statusCode(200);
    }

    private Socket openUpload(String id, long length) throws Exception {
        return openRequest(
                "PUT",
                content(id),
                length,
                "audio/flac");
    }

    private Socket openRequest(
            String method,
            String path,
            long length,
            String contentType) throws Exception {
        final var socket = new Socket(endpoint.getHost(), endpoint.getPort());
        socket.setSoTimeout((int) Duration.ofSeconds(25).toMillis());
        final var headers = method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Authorization: Bearer test-admin-api-key\r\nContent-Type: " + contentType + "\r\nContent-Length: "
                + length
                + "\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        return socket;
    }

    private static String status(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
    }

    private static String reserve() {
        return authorized().post(BASE).then().statusCode(201).extract().path("audioId");
    }

    private static String bucket() {
        return ConfigProvider.getConfig().getValue("abservice.private-audio.bucket", String.class);
    }

    @Test
    @DisplayName("HTTP2でも不正FLACに有効な400応答を返す")
    void rejectsInvalidFlacOverHttp2() throws Exception {
        final var id = reserve();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build()) {
            final var request = HttpRequest.newBuilder(endpoint.resolve(content(id)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer test-admin-api-key").header("Content-Type", "audio/flac")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{102, 76, 97, 67})).build();
            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    private static String content(String id) {
        return BASE + "/" + id + "/content";
    }

    private static String key(String id) {
        return "audio/verified/" + id + ".flac";
    }

    private static void update(String id, String assignment) throws Exception {
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.prepareStatement(
                        "UPDATE private_audio_registration SET " + assignment + " WHERE audio_id = ?")) {
            statement.setObject(1, UUID.fromString(id));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private byte[] fixture(int size) throws Exception {
        return Files.readAllBytes(fixtureFile(size));
    }

    private Path fixtureFile(int size) throws Exception {
        final var input = directory.resolve(UUID.randomUUID() + ".raw");
        final var output = input.resolveSibling(input.getFileName() + ".flac");
        final var random = new Random(474);
        final byte[] block = new byte[4096];
        try (var raw = Files.newOutputStream(input)) {
            for (int written = 0; written < size; written += block.length) {
                random.nextBytes(block);
                raw.write(
                        block,
                        0,
                        Math.min(block.length, size - written));
            }
        }
        final var encoder = new ProcessBuilder("flac", "--silent", "--force-raw-format", "--endian=little",
                "--sign=signed",
                "--channels=2", "--bps=16", "--sample-rate=44100", "--no-padding", "--no-seektable",
                "--output-name=" + output, input.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            assertThat(encoder.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(encoder.exitValue()).isZero();
            return output;
        } finally {
            encoder.destroyForcibly().onExit().join();
        }
    }
}
