package com.abservice.presentation.rest.asset;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * アセットアップロード REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/assets/upload-url}（署名付きURL発行）→ 署名付きURLへの直接 PUT →
 * {@code POST /api/v1/assets/{assetKey}/confirm}（実体検査と確定）の疎通と、実体がキーの形式と一致しない場合・
 * サイズ上限を超える場合・実体が無い場合の拒否を確認する。実ストレージ（docker compose の MinIO。バケットは
 * {@code minio-init} が作成）で動作する。テストの上限は {@code abservice.assets.max-bytes=1024}。
 * </p>
 */
@QuarkusTest
@DisplayName("アセットアップロード REST エンドポイントの統合テスト")
class AssetUploadRestIntegrationTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'};

    @Inject
    private S3AsyncClient s3;

    @Inject
    @ConfigProperty(name = "abservice.assets.bucket")
    private String bucket;

    @Inject
    @ConfigProperty(name = "abservice.assets.public-base-path")
    private String publicBasePath;

    @Inject
    @ConfigProperty(name = "abservice.assets.pending-prefix")
    private String pendingPrefix;

    @Test
    @DisplayName("URL発行→PUT→確定で公開配信URLが得られる")
    void issueUploadConfirm() {
        final var issued = issueUploadUrl("image/png");
        final String assetKey = issued.path("assetKey");

        putContent(
                issued.path("uploadUrl"),
                "image/png",
                pngBytes(256));

        authorized().when().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(200)
                .body("assetKey", equalTo(assetKey)).body("url", equalTo("/assets/" + assetKey))
                .body("contentType", equalTo("image/png")).body("sizeBytes", equalTo(256));
    }

    @Test
    @DisplayName("確定前の実体は配信対象の場所に無く、確定で配信対象へ移り受け入れ前の実体は残らない")
    void confirmMovesContentFromPendingToPublished() {
        final var issued = issueUploadUrl("image/png");
        final String assetKey = issued.path("assetKey");

        putContent(
                issued.path("uploadUrl"),
                "image/png",
                pngBytes(256));

        assertThat(objectBytes(pendingKey(assetKey))).as("確定前は受け入れ前に存在する").isPresent();
        assertThat(objectBytes(publishedKey(assetKey))).as("確定前は配信対象に存在しない").isEmpty();

        authorized().when().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(200);

        assertThat(objectBytes(publishedKey(assetKey))).as("確定後は配信対象に存在する").isPresent();
        assertThat(objectBytes(pendingKey(assetKey))).as("確定後は受け入れ前に残らない").isEmpty();
    }

    @Test
    @DisplayName("確定後に同じ署名付きURLへ再アップロードしても、配信される実体は変わらない")
    void reuploadAfterConfirmDoesNotChangePublishedContent() {
        final var issued = issueUploadUrl("image/png");
        final String assetKey = issued.path("assetKey");
        final String uploadUrl = issued.path("uploadUrl");

        putContent(
                uploadUrl,
                "image/png",
                pngBytes(256));
        authorized().when().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(200)
                .body("sizeBytes", equalTo(256));

        putContent(
                uploadUrl,
                "image/png",
                pngBytes(512));

        assertThat(objectBytes(publishedKey(assetKey)))
                .as("確定済みの配信実体は差し替わらない")
                .get()
                .extracting(bytes -> bytes.length)
                .isEqualTo(256);
    }

    @Test
    @DisplayName("発行されたキーは拡張子付きで、URLは署名クエリを伴う")
    void issuedKeyAndUrlShape() {
        final var response = authorized().contentType(ContentType.JSON).body("{\"contentType\":\"image/jpeg\"}")
                .when().post("/api/v1/assets/upload-url").then().statusCode(200)
                .body("uploadUrl", startsWith("http")).body("maxBytes", equalTo(1024)).extract();

        assertThat(response.<String>path("assetKey")).endsWith(".jpg");
        assertThat(response.<String>path("uploadUrl")).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("実体が画像でない場合は確定を400で拒否する")
    void confirmRejectsNonImageContent() {
        final var issued = issueUploadUrl("image/png");
        final String assetKey = issued.path("assetKey");

        putContent(
                issued.path("uploadUrl"),
                "image/png",
                "not an image at all".getBytes(StandardCharsets.UTF_8));

        authorized().when().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].code", equalTo("ASSET_CONTENT_MISMATCH"));
    }

    @Test
    @DisplayName("上限を超えるサイズは確定を400で拒否し、以後は実体が消えている")
    void confirmRejectsOversizedContent() {
        final var issued = issueUploadUrl("image/png");
        final String assetKey = issued.path("assetKey");

        putContent(
                issued.path("uploadUrl"),
                "image/png",
                pngBytes(2048));

        authorized().when().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].code", equalTo("ASSET_TOO_LARGE"));

        authorized().when().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("アップロードされていないキーの確定は404を返す")
    void confirmUnknownKeyIsNotFound() {
        authorized().when().post("/api/v1/assets/0192f8a0-0000-7000-8000-0000000000ff.png/confirm").then()
                .statusCode(404).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("受け入れ対象外の形式は400 problem+jsonを返す")
    void unsupportedContentTypeIsRejected() {
        authorized().contentType(ContentType.JSON).body("{\"contentType\":\"image/gif\"}").when()
                .post("/api/v1/assets/upload-url").then().statusCode(400)
                .contentType("application/problem+json")
                .body("errors[0].code", equalTo("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    @DisplayName("APIキー無しのURL発行・確定は401を返す")
    void requiresAdminApiKey() {
        given().contentType(ContentType.JSON).body("{\"contentType\":\"image/png\"}").when()
                .post("/api/v1/assets/upload-url").then().statusCode(401);

        given().when().post("/api/v1/assets/0192f8a0-0000-7000-8000-0000000000ff.png/confirm").then()
                .statusCode(401);
    }

    private static ExtractableResponse<Response> issueUploadUrl(String contentType) {
        return authorized().contentType(ContentType.JSON).body("{\"contentType\":\"" + contentType + "\"}").when()
                .post("/api/v1/assets/upload-url").then().statusCode(200).extract();
    }

    /**
     * 署名付きURLへ実体を PUT します。
     *
     * <p>
     * 署名は URL と {@code Content-Type} を対象に計算されているため、RestAssured の既定動作を2点無効化する:
     * URLエンコード（有効なままでは署名クエリが二重エンコードされる）と、Content-Type への charset 付与
     * （{@code image/png; charset=ISO-8859-1} になり署名と一致しない）。
     * </p>
     *
     * @param uploadUrl
     *            署名付きURL
     * @param contentType
     *            署名時に束縛した Content-Type
     * @param content
     *            送信する実体
     */
    private static void putContent(
            String uploadUrl,
            String contentType,
            byte[] content) {
        given().config(
                RestAssured.config()
                        .encoderConfig(
                                EncoderConfig.encoderConfig()
                                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .urlEncodingEnabled(false).contentType(contentType).body(content).when().put(uploadUrl).then()
                .log().ifValidationFails().statusCode(200);
    }

    private static byte[] pngBytes(int totalBytes) {
        return Arrays.copyOf(PNG_SIGNATURE, totalBytes);
    }

    private String pendingKey(String assetKey) {
        return pendingPrefix + "/" + assetKey;
    }

    private String publishedKey(String assetKey) {
        return publicBasePath.replaceFirst("^/", "") + "/" + assetKey;
    }

    /**
     * 保管先のオブジェクトを直接読み出します（配信経路を介さず、実体そのものを確かめるため）。
     *
     * @param key
     *            オブジェクトキー（接頭辞を含む）
     * @return 実体が存在すればそのバイト列、存在しなければ空
     */
    private Optional<byte[]> objectBytes(String key) {
        try {
            final var response = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    AsyncResponseTransformer.toBytes()).join();
            return Optional.of(response.asByteArray());
        } catch (CompletionException failure) {
            assertThat(failure).hasCauseInstanceOf(NoSuchKeyException.class);
            return Optional.empty();
        }
    }
}
