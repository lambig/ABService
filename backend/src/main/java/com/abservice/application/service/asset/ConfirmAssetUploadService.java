package com.abservice.application.service.asset;

import com.abservice.application.port.AssetConfirmConflictException;
import com.abservice.application.port.AssetStorage;
import com.abservice.application.port.PublishedAsset;
import com.abservice.application.port.PublishedAssets;
import com.abservice.application.port.StoredAssetDigest;
import com.abservice.application.port.StoredAssetHead;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * アップロード確定ユースケース
 *
 * <p>
 * クライアントが署名付きURLへ実体を送り終えた後に呼ばれ、受け入れ前の実体を検査してから配信対象として確定し、公開配信URLを
 * 返す。検査は先頭バイト列の1回の範囲取得で行い、サイズ・形式（マジックバイト）・払い出したキーの拡張子との一致を確認する。
 * 検査に通らない実体は破棄して検証エラーにするため、クライアントの申告値を信用せずに済む。
 * </p>
 *
 * <p>
 * 確定は受け入れ前の場所から配信対象へ実体を移す操作であり、クライアントが書き込めるのは受け入れ前だけ。検査した実体と
 * 配信される実体がずれないよう、「検査したその実体であること」と「そのキーがまだ確定していないこと」の両方を、実体を移す
 * 操作そのものの条件にする（#285）。確定済みかどうかを先に見るのは、確定済みの要求を早く断って無駄な検査を省くため。
 * 同時に走る確定を退けるのはこの問い合わせではなく、移す操作の条件である。
 * </p>
 *
 * <p>
 * 確定が途中で止まった場合の状態は次のように定まる。配信対象へのコピーが済んだ後に受け入れ前の片付けが失敗しても、確定は
 * 成功として返る。残った受け入れ前の実体は保管先のライフサイクルで期限切れになる（{@code docs/DECISIONS.md} 18）。
 * 応答がクライアントへ届かずに確定が再送された場合は、公開キーが確定済みであるため競合として断られ、配信される実体は 変わらない。
 * </p>
 *
 * <p>
 * 確定した実体の実測値（バイト数と SHA-256）は、確定のコピーで保管先に計算させた値を読み、独立したトランザクションで記録する。
 * 試聴端末へ渡す表示素材の識別に使う。記録の失敗は確定を失敗にしない。確定済みで記録の無いキーをもう一度確定すると、
 * コピーをせずに記録だけを補って成功を返す（記録済みなら従来どおり競合）。要求の {@code @WithTransaction} は付与しない
 * （記録は自前の commit で完結する）。
 * </p>
 */
@ApplicationScoped
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class ConfirmAssetUploadService implements CommandService<ConfirmAssetUploadInput, ConfirmAssetUploadOutput> {

    private static final Logger LOG = Logger.getLogger(ConfirmAssetUploadService.class);

    private final AssetStorage assetStorage;
    private final PublishedAssets publishedAssets;
    private final long maxBytes;
    private final String publicBasePath;

    /**
     * @param assetStorage
     *            アセット保管先
     * @param publishedAssets
     *            確定した実体の実測値の記録先
     * @param maxBytes
     *            許容する最大バイト数（{@code abservice.assets.max-bytes}）
     * @param publicBasePath
     *            公開配信URLのベースパス（{@code abservice.assets.public-base-path}）
     */
    public ConfirmAssetUploadService(
            AssetStorage assetStorage,
            PublishedAssets publishedAssets,
            @ConfigProperty(name = "abservice.assets.max-bytes") long maxBytes,
            @ConfigProperty(name = "abservice.assets.public-base-path") String publicBasePath) {
        this.assetStorage = assetStorage;
        this.publishedAssets = publishedAssets;
        this.maxBytes = maxBytes;
        this.publicBasePath = publicBasePath;
    }

    @Override
    public Uni<ConfirmAssetUploadOutput> execute(ConfirmAssetUploadInput input) {
        return assetStorage.isPublished(input.assetKey())
                .flatMap(published -> confirmUnlessPublished(input.assetKey(), published));
    }

    /**
     * 一度確定した公開キーへ、別の実体を後から乗せない。確定済みのキーは、同じ署名付きURLで受け入れ前を作り直して
     * 確定をやり直しても置き換わらない（#285）。確定済みで実測値の記録だけが無いキーは、コピーをせずに記録を補う。
     */
    private Uni<ConfirmAssetUploadOutput> confirmUnlessPublished(String assetKey, boolean published) {
        return published
                ? repairUnlessRecorded(assetKey)
                : inspectAndConfirm(assetKey);
    }

    private Uni<ConfirmAssetUploadOutput> repairUnlessRecorded(String assetKey) {
        return publishedAssets.isRecorded(assetKey)
                .flatMap(recorded -> repairOrReject(assetKey, recorded));
    }

    private Uni<ConfirmAssetUploadOutput> repairOrReject(String assetKey, boolean recorded) {
        return recorded
                ? Uni.createFrom().failure(alreadyPublished(assetKey))
                : repair(assetKey);
    }

    /**
     * 確定済みの実体から実測値を読み直して記録する。配信対象は変えないため、同じキーへ何度送っても配信される実体は同じ。
     * ここでの失敗はそのまま返し、呼び出し側は同じキーで再送できる。
     */
    private Uni<ConfirmAssetUploadOutput> repair(String assetKey) {
        return AssetImageFormat.ofExtension(extensionOf(assetKey))
                .map(format -> repaired(assetKey, format))
                .orElseGet(() -> Uni.createFrom().failure(alreadyPublished(assetKey)));
    }

    private Uni<ConfirmAssetUploadOutput> repaired(String assetKey, AssetImageFormat format) {
        return assetStorage.readPublishedDigest(assetKey)
                .call(
                        digest -> publishedAssets.record(
                                publishedAsset(
                                        assetKey,
                                        format,
                                        digest)))
                .map(
                        digest -> output(
                                assetKey,
                                format,
                                digest.byteLength()));
    }

    private static PublishedAsset publishedAsset(
            String assetKey,
            AssetImageFormat format,
            StoredAssetDigest digest) {
        return new PublishedAsset(
                assetKey,
                format.contentType(),
                digest.byteLength(),
                digest.sha256());
    }

    private Uni<ConfirmAssetUploadOutput> inspectAndConfirm(String assetKey) {
        return assetStorage.readHead(assetKey, AssetImageFormat.REQUIRED_PREFIX_BYTES)
                .flatMap(head -> verify(assetKey, head));
    }

    private Uni<ConfirmAssetUploadOutput> verify(String assetKey, Optional<StoredAssetHead> head) {
        return head
                .map(stored -> verifyStored(assetKey, stored))
                .orElseGet(() -> Uni.createFrom().failure(EntityNotFoundException.of("Asset", assetKey)));
    }

    private Uni<ConfirmAssetUploadOutput> verifyStored(String assetKey, StoredAssetHead stored) {
        return sizeViolation(stored)
                .map(error -> reject(assetKey, error))
                .orElseGet(() -> verifyContent(assetKey, stored));
    }

    private Uni<ConfirmAssetUploadOutput> verifyContent(String assetKey, StoredAssetHead stored) {
        return detectedFormat(assetKey, stored)
                .map(
                        format -> confirmed(
                                assetKey,
                                stored,
                                format))
                .orElseGet(() -> reject(assetKey, contentMismatch(assetKey)));
    }

    private Uni<ConfirmAssetUploadOutput> confirmed(
            String assetKey,
            StoredAssetHead stored,
            AssetImageFormat format) {
        return assetStorage.publish(assetKey, stored.entityTag())
                .onFailure(AssetConfirmConflictException.class)
                .transform(cause -> confirmConflict(assetKey, cause))
                .chain(() -> recordDigestIfPossible(assetKey, format))
                .replaceWith(
                        () -> output(
                                assetKey,
                                format,
                                stored.totalBytes()));
    }

    private ConfirmAssetUploadOutput output(
            String assetKey,
            AssetImageFormat format,
            long sizeBytes) {
        return new ConfirmAssetUploadOutput(
                assetKey,
                publicBasePath + "/" + assetKey,
                format.contentType(),
                sizeBytes);
    }

    /**
     * 確定した実体の実測値を保管先から読み、独立commitで記録する。
     *
     * <p>
     * 記録は試聴端末へ渡す表示素材のための付加情報で、配信は確定のコピーで既に成立している。ここでの失敗で確定を失敗として
     * 返すと、呼び出し側は「確定できていない」と誤って読み、再送は確定済みとして断られる。失敗は警告に留めて確定を成功として
     * 返し、記録は同じキーの再確定で補う。
     * </p>
     */
    private Uni<Void> recordDigestIfPossible(String assetKey, AssetImageFormat format) {
        return assetStorage.readPublishedDigest(assetKey)
                .chain(
                        digest -> publishedAssets.record(
                                publishedAsset(
                                        assetKey,
                                        format,
                                        digest)))
                .onFailure().invoke(failure -> warnUnrecorded(assetKey, failure))
                .onFailure().recoverWithNull();
    }

    private static void warnUnrecorded(String assetKey, Throwable failure) {
        LOG.warnf(
                failure,
                "確定した画像の実測値を記録できませんでした。同じキーの再確定で補えます: key=%s",
                assetKey);
    }

    /**
     * 確定済みの公開キーをもう一度確定しようとした場合の競合。やり直しは別のキーを払い出して行う。
     */
    private static BusinessRuleViolationException alreadyPublished(String assetKey) {
        return new BusinessRuleViolationException(
                "このアセットは確定済みです。別のキーを払い出してからアップロードしてください: key=" + assetKey);
    }

    /**
     * 保管先が確定の条件を満たさなかった場合の競合。保管先の事情を、呼び出し側が読み取れる競合へ翻訳する。
     */
    private static BusinessRuleViolationException confirmConflict(String assetKey, Throwable cause) {
        return new BusinessRuleViolationException(
                "このアセットを確定できません。実体が置き換わったか、既に確定済みです: key=" + assetKey,
                cause);
    }

    private Uni<ConfirmAssetUploadOutput> reject(String assetKey, ErrorResult error) {
        return assetStorage.discard(assetKey)
                .replaceWith(
                        Uni.createFrom().failure(
                                new ValidationException(List.of(error))));
    }

    private Optional<ErrorResult> sizeViolation(StoredAssetHead stored) {
        return Optional.of(stored)
                .filter(head -> head.totalBytes() > maxBytes)
                .map(
                        head -> new ErrorResult(
                                "file",
                                "サイズが上限を超えています: " + head.totalBytes() + " バイト（上限 " + maxBytes + " バイト）",
                                "ASSET_TOO_LARGE"));
    }

    private static Optional<AssetImageFormat> detectedFormat(String assetKey, StoredAssetHead stored) {
        return AssetImageFormat.ofContent(stored.prefix())
                .filter(detected -> matchesKeyExtension(assetKey, detected));
    }

    private static boolean matchesKeyExtension(String assetKey, AssetImageFormat detected) {
        return AssetImageFormat.ofExtension(extensionOf(assetKey))
                .stream()
                .anyMatch(detected::equals);
    }

    private static String extensionOf(String assetKey) {
        return assetKey.substring(assetKey.lastIndexOf('.') + 1);
    }

    private static ErrorResult contentMismatch(String assetKey) {
        return new ErrorResult(
                "file",
                "実体が発行したキーの形式と一致しません: key=" + assetKey,
                "ASSET_CONTENT_MISMATCH");
    }
}
