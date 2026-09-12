package com.abservice.infrastructure.storage;

import com.abservice.application.port.AssetConfirmConflictException;
import com.abservice.application.port.AssetStorage;
import com.abservice.application.port.PresignedUpload;
import com.abservice.application.port.StoredAssetHead;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3互換オブジェクトストレージによる {@link AssetStorage} 実装
 *
 * <p>
 * アップロードは署名付きURLでクライアントから直接行われるため、本アダプタは実体のバイト列を中継しない。実体の検査は先頭 バイト列の範囲取得（Range
 * GET）1回で行い、応答の {@code Content-Range} から全体サイズを得る。
 * </p>
 *
 * <p>
 * 受け入れ前の実体は配信パスとは別の接頭辞（{@code abservice.assets.pending-prefix}）へ置く。CloudFront が
 * 配信するのは配信パスの接頭辞だけなので、受け入れ前の実体は外部から到達できない。確定はサーバ側コピー
 * （{@code CopyObject}）で行うため、実体がバックエンドを経由することはない。
 * </p>
 *
 * <p>
 * 確定の正しさは {@code CopyObject} 自身の条件に置く。コピー元は検査した実体であること
 * （{@code x-amz-copy-source-if-match}）、コピー先はまだ無いこと（{@code If-None-Match: *}）を1回の操作で
 * 同時に満たす場合だけ実体が移る。前段の {@link #isPublished} は確定済みの要求を早く断るためのもので、
 * これ自体は同時確定を防がない（#285）。
 * </p>
 *
 * <p>
 * コピー先の条件は S3 が受け持つ。S3互換であるローカルの MinIO は現状これを無視して素通りさせるため、
 * ローカルで通る同時確定が本番で通るとは限らない（逆は起きない）。条件を送っていること自体は
 * {@code S3AssetStorageRequestTest} が固定する。
 * </p>
 */
@ApplicationScoped
public class S3AssetStorage implements AssetStorage {

    private static final Logger LOG = Logger.getLogger(S3AssetStorage.class);

    /** 確定の条件を満たせなかったことを表す状態コード（順に、条件の不成立／コピー元の不在／同時書き込みの競合） */
    private static final Set<Integer> CONFIRM_CONFLICT = Set.of(
            412,
            404,
            409);

    /** コピー先がまだ無いことを表す条件（{@code If-None-Match} のワイルドカード） */
    private static final String ANY_ENTITY_TAG = "*";

    private final S3AsyncClient s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publishedPrefix;
    private final String pendingPrefix;
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
     *            除いたものを配信対象のオブジェクトキーの接頭辞として使い、配信パスと保管キーを一致させる
     * @param pendingPrefix
     *            受け入れ前のオブジェクトキーの接頭辞（{@code abservice.assets.pending-prefix}）
     * @param presignExpiry
     *            署名付きURLの有効期間（{@code abservice.assets.presign-expiry}）
     */
    public S3AssetStorage(
            S3AsyncClient s3,
            S3Presigner presigner,
            @ConfigProperty(name = "abservice.assets.bucket") String bucket,
            @ConfigProperty(name = "abservice.assets.public-base-path") String publicBasePath,
            @ConfigProperty(name = "abservice.assets.pending-prefix") String pendingPrefix,
            @ConfigProperty(name = "abservice.assets.presign-expiry") Duration presignExpiry) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
        this.publishedPrefix = publicBasePath.replaceFirst("^/", "");
        this.pendingPrefix = pendingPrefix;
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
    public Uni<Void> publish(String key, String entityTag) {
        return Uni.createFrom()
                .completionStage(() -> s3.copyObject(publishRequest(key, entityTag)))
                .onFailure(S3AssetStorage::isConfirmConflict)
                .transform(failure -> new AssetConfirmConflictException(key, failure))
                .chain(() -> discardLeftover(key));
    }

    /**
     * 確定のコピー要求を組み立てます。
     *
     * <p>
     * 2つの条件を同じ操作へ載せる。{@code copySourceIfMatch} はコピー元が検査した実体のままであること、
     * {@code ifNoneMatch("*")} はコピー先がまだ無いことを表す。片方だけでは、検査から確定までの置き換わりと
     * 確定済みキーの上書きのどちらかが残る（#285）。
     * </p>
     *
     * @param key
     *            アセットキー
     * @param entityTag
     *            検査した実体の識別子
     * @return コピー要求
     */
    private CopyObjectRequest publishRequest(String key, String entityTag) {
        return CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(pendingKey(key))
                .destinationBucket(bucket)
                .destinationKey(publishedKey(key))
                .copySourceIfMatch(entityTag)
                .ifNoneMatch(ANY_ENTITY_TAG)
                .build();
    }

    /**
     * 確定を終えた受け入れ前の実体を片付けます。
     *
     * <p>
     * ここでの失敗は確定を取り消さない。実体は既に配信対象へ移っており、公開キーは確定済みとして以後の確定を拒む。
     * 残った受け入れ前の実体は保管先のライフサイクルで期限切れになる（{@code docs/DECISIONS.md} 18）。
     * 片付けの失敗で確定を失敗として返すと、呼び出し側が「確定できていない」と誤って読む。
     * </p>
     *
     * @param key
     *            アセットキー
     * @return 完了（片付けに失敗しても成功として返す）
     */
    private Uni<Void> discardLeftover(String key) {
        return discard(key)
                .onFailure().invoke(failure -> warnLeftover(key, failure))
                .onFailure().recoverWithNull();
    }

    private static void warnLeftover(String key, Throwable failure) {
        LOG.warnf(
                failure,
                "確定後の受け入れ前の実体を片付けられませんでした: key=%s",
                key);
    }

    @Override
    public Uni<Boolean> isPublished(String key) {
        return Uni.createFrom()
                .completionStage(
                        () -> s3.headObject(
                                HeadObjectRequest.builder()
                                        .bucket(bucket)
                                        .key(publishedKey(key))
                                        .build()))
                .replaceWith(true)
                .onFailure(NoSuchKeyException.class).recoverWithItem(false);
    }

    @Override
    public Uni<Void> discard(String key) {
        return Uni.createFrom()
                .completionStage(
                        () -> s3.deleteObject(
                                DeleteObjectRequest.builder()
                                        .bucket(bucket)
                                        .key(pendingKey(key))
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
                                        .key(pendingKey(key))
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
                .key(pendingKey(key))
                .range("bytes=0-" + (length - 1))
                .build();
    }

    private static Optional<StoredAssetHead> toStoredHead(ResponseBytes<GetObjectResponse> response) {
        return Optional.of(
                new StoredAssetHead(
                        response.asByteArray(),
                        totalBytes(response.response()),
                        response.response().contentType(),
                        response.response().eTag()));
    }

    /**
     * 確定の条件を満たせなかったか。コピー元が別の実体へ置き換わっていた場合とコピー先が既にある場合は 412 （実測: MinIO
     * もコピー元の条件については同じ）、先行する確定が受け入れ前を片付けた後はコピー元が無く 404、 コピー先への同時書き込みが競合した場合は
     * 409。いずれも「このキーへは確定できない」であり、呼び出し側の対処は 変わらないため区別しない。
     */
    private static boolean isConfirmConflict(Throwable failure) {
        return Optional.of(failure)
                .map(S3AssetStorage::rootCause)
                .filter(S3Exception.class::isInstance)
                .map(S3Exception.class::cast)
                .filter(s3Failure -> CONFIRM_CONFLICT.contains(s3Failure.statusCode()))
                .isPresent();
    }

    private static Throwable rootCause(Throwable failure) {
        return Stream.iterate(
                failure,
                Objects::nonNull,
                Throwable::getCause)
                .filter(candidate -> Objects.isNull(candidate.getCause()))
                .findFirst()
                .orElse(failure);
    }

    private static long totalBytes(GetObjectResponse response) {
        return Optional.ofNullable(response.contentRange())
                .map(range -> range.substring(range.lastIndexOf('/') + 1))
                .map(Long::parseLong)
                .orElseGet(response::contentLength);
    }

    private String pendingKey(String key) {
        return pendingPrefix + "/" + key;
    }

    private String publishedKey(String key) {
        return publishedPrefix + "/" + key;
    }
}
