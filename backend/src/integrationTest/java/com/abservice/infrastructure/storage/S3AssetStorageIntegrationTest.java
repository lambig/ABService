package com.abservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.AssetConfirmConflictException;
import com.abservice.application.port.StoredAssetHead;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 確定が検査した実体に結び付いていることの統合テスト
 *
 * <p>
 * 受け入れ前のキーへは署名付きURLの有効期間内なら何度でも書き込めるため、検査と確定の間に実体が置き換わり得る。確定は
 * 検査で得た識別子（{@code ETag}）を条件に取り、置き換わっていれば実ストレージが拒む（#285）。実ストレージ（docker compose の
 * MinIO。バケットは {@code minio-init} が作成）で動作する。
 * </p>
 *
 * <p>
 * 確定のもう一方の条件であるコピー先の不在（{@code If-None-Match}）は MinIO が無視するため、ここでは観測できない。
 * 要求へ載せていることは {@code S3AssetStorageRequestTest} が固定する。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("S3AssetStorage（確定と検査済み実体の結び付け）の統合テスト")
class S3AssetStorageIntegrationTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'};

    private static final int PREFIX_BYTES = 12;

    @Inject
    private S3AssetStorage storage;

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
    @DisplayName("検査した実体のままであれば確定でき、受け入れ前の実体は残らない")
    void publishesInspectedContent() {
        final var assetKey = assetKey();
        putPending(assetKey, pngBytes(256));

        final var head = readHead(assetKey);
        storage.publish(assetKey, head.entityTag()).await().indefinitely();

        assertThat(objectBytes(publishedKey(assetKey))).as("配信対象へ移る").get()
                .extracting(bytes -> bytes.length).isEqualTo(256);
        assertThat(objectBytes(pendingKey(assetKey))).as("受け入れ前には残らない").isEmpty();
    }

    @Test
    @DisplayName("検査から確定までの間に受け入れ前が置き換わると確定できず、配信対象には何も置かれない")
    void rejectsPublishingContentReplacedAfterInspection() {
        final var assetKey = assetKey();
        putPending(assetKey, pngBytes(256));
        final var head = readHead(assetKey);

        putPending(assetKey, pngBytes(512));

        assertThatThrownBy(() -> storage.publish(assetKey, head.entityTag()).await().indefinitely())
                .isInstanceOf(AssetConfirmConflictException.class)
                .hasMessageContaining(assetKey);

        assertThat(objectBytes(publishedKey(assetKey))).as("検査していない実体は配信対象へ移らない").isEmpty();
    }

    @Test
    @DisplayName("配信対象へ移った実体の形式とサイズは、検査した実体のものと一致する")
    void publishedContentKeepsInspectedTypeAndSize() {
        final var assetKey = assetKey();
        putPending(assetKey, pngBytes(256));

        final var head = readHead(assetKey);
        storage.publish(assetKey, head.entityTag()).await().indefinitely();

        final var published = objectHead(publishedKey(assetKey));
        assertThat(published.contentType()).as("形式").isEqualTo(head.contentType());
        assertThat(published.contentLength()).as("サイズ").isEqualTo(head.totalBytes());
    }

    /**
     * 同時確定が1つに収束することは、コピー先の条件（{@code If-None-Match}）が担う。MinIO はこれを無視するため
     * ここでは観測できず、要求へ載せていることは {@code S3AssetStorageRequestTest} が固定する。この試験が見るのは、
     * 同時に走らせても配信されるのは検査した実体であり、確定できなかった側が競合として返ること。
     */
    @Test
    @DisplayName("同一キーの確定が同時に走っても、配信されるのは検査した実体で、確定できなかった側は競合になる")
    void concurrentPublishKeepsInspectedContent() {
        final var assetKey = assetKey();
        putPending(assetKey, pngBytes(256));
        final var entityTag = readHead(assetKey).entityTag();

        final var first = publishAsync(assetKey, entityTag);
        final var second = publishAsync(assetKey, entityTag);
        final var outcomes = Stream.of(first, second)
                .map(CompletableFuture::join)
                .toList();

        assertThat(outcomes).as("確定できなかった側も競合として畳まれ、想定外の失敗にならない").contains(true);
        assertThat(objectBytes(publishedKey(assetKey))).as("配信されるのは検査した実体").get()
                .extracting(bytes -> bytes.length).isEqualTo(256);
        assertThat(objectBytes(pendingKey(assetKey))).as("受け入れ前には残らない").isEmpty();
    }

    @Test
    @DisplayName("確定済みかどうかを配信対象の実体で判別する")
    void reportsWhetherKeyIsAlreadyPublished() {
        final var assetKey = assetKey();
        putPending(assetKey, pngBytes(256));

        assertThat(storage.isPublished(assetKey).await().indefinitely()).as("確定前").isFalse();

        storage.publish(assetKey, readHead(assetKey).entityTag()).await().indefinitely();

        assertThat(storage.isPublished(assetKey).await().indefinitely()).as("確定後").isTrue();
    }

    /**
     * 確定を別スレッドで走らせます（同時確定を作るため）。
     *
     * @param assetKey
     *            アセットキー
     * @param entityTag
     *            検査した実体の識別子
     * @return 確定できたかどうか（競合で弾かれた場合は {@code false}）
     */
    private CompletableFuture<Boolean> publishAsync(String assetKey, String entityTag) {
        return CompletableFuture.supplyAsync(
                () -> storage.publish(assetKey, entityTag)
                        .replaceWith(true)
                        .onFailure(AssetConfirmConflictException.class).recoverWithItem(false)
                        .await().indefinitely());
    }

    private HeadObjectResponse objectHead(String key) {
        return s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).join();
    }

    private StoredAssetHead readHead(String assetKey) {
        return storage.readHead(assetKey, PREFIX_BYTES).await().indefinitely().orElseThrow();
    }

    private void putPending(String assetKey, byte[] content) {
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(pendingKey(assetKey)).contentType("image/png").build(),
                AsyncRequestBody.fromBytes(content)).join();
    }

    private static String assetKey() {
        return UUID.randomUUID() + ".png";
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
     * 保管先のオブジェクトを直接読み出します（実体そのものを確かめるため）。
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
