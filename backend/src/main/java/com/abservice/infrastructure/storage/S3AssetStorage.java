package com.abservice.infrastructure.storage;

import com.abservice.application.port.AssetStorage;
import com.abservice.application.port.PresignedUpload;
import com.abservice.application.port.StoredAssetHead;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3互換オブジェクトストレージによる {@link AssetStorage} 実装
 *
 * <p>
 * アップロードは署名付きURLでクライアントから直接行われるため、本アダプタは実体のバイト列を中継しない。実体の検査は先頭 バイト列の範囲取得（Range
 * GET）1回で行い、応答の {@code Content-Range} から全体サイズを得る。
 * </p>
 */
@ApplicationScoped
public class S3AssetStorage implements AssetStorage {

    private final S3AsyncClient s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String keyPrefix;
    private final Duration presignExpiry;

    /**
     * @param s3
     *            非同期S3クライアント
     * @param presigner
     *            署名付きURL生成器
     * @param bucket
     *            アセット保管バケット（{@code abservice.assets.bucket}）
     * @param publicBasePath
     *            公開配信URLのベースパス（{@code abservice.assets.public-base-path}）。先頭のスラッシュを
     *            除いたものをオブジェクトキーの接頭辞として使い、配信パスと保管キーを一致させる
     * @param presignExpiry
     *            署名付きURLの有効期間（{@code abservice.assets.presign-expiry}）
     */
    public S3AssetStorage(
            S3AsyncClient s3,
            S3Presigner presigner,
            @ConfigProperty(name = "abservice.assets.bucket") String bucket,
            @ConfigProperty(name = "abservice.assets.public-base-path") String publicBasePath,
            @ConfigProperty(name = "abservice.assets.presign-expiry") Duration presignExpiry) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
        this.keyPrefix = publicBasePath.replaceFirst("^/", "");
        this.presignExpiry = presignExpiry;
    }

    @Override
    public Uni<PresignedUpload> presignUpload(String key, String contentType) {
        return Uni.createFrom().item(() -> presign(key, contentType));
    }

    @Override
    public Uni<Optional<StoredAssetHead>> readHead(String key, int length) {
        return Uni.createFrom()
                .completionStage(
                        () -> s3.getObject(
                                headRequest(key, length),
                                AsyncResponseTransformer.toBytes()))
                .map(S3AssetStorage::toStoredHead)
                .onFailure(NoSuchKeyException.class).recoverWithItem(Optional.empty());
    }

    @Override
    public Uni<Void> delete(String key) {
        return Uni.createFrom()
                .completionStage(
                        () -> s3.deleteObject(
                                DeleteObjectRequest.builder()
                                        .bucket(bucket)
                                        .key(objectKey(key))
                                        .build()))
                .replaceWithVoid();
    }

    private PresignedUpload presign(String key, String contentType) {
        final var presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(presignExpiry)
                        .putObjectRequest(
                                PutObjectRequest.builder()
                                        .bucket(bucket)
                                        .key(objectKey(key))
                                        .contentType(contentType)
                                        .build())
                        .build());
        return new PresignedUpload(
                presigned.url().toString(),
                presigned.expiration());
    }

    private GetObjectRequest headRequest(String key, int length) {
        return GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey(key))
                .range("bytes=0-" + (length - 1))
                .build();
    }

    private static Optional<StoredAssetHead> toStoredHead(ResponseBytes<GetObjectResponse> response) {
        return Optional.of(
                new StoredAssetHead(
                        response.asByteArray(),
                        totalBytes(response.response()),
                        response.response().contentType()));
    }

    private static long totalBytes(GetObjectResponse response) {
        return Optional.ofNullable(response.contentRange())
                .map(range -> range.substring(range.lastIndexOf('/') + 1))
                .map(Long::parseLong)
                .orElseGet(response::contentLength);
    }

    private String objectKey(String key) {
        return keyPrefix + "/" + key;
    }
}
