package com.abservice.application.service.asset;

import com.abservice.application.port.AssetChangedDuringConfirmException;
import com.abservice.application.port.AssetStorage;
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
 * 配信される実体がずれないよう、2つの経路を塞ぐ（#285）。検査から確定までの隙間に受け入れ前が置き換わった場合は、
 * 確定が検査した実体を条件にしているため失敗する。確定後に受け入れ前を作り直して確定をやり直す場合は、確定済みの 公開キーであることを先に見て拒む。
 * </p>
 *
 * <p>
 * 確定が途中で止まった場合の状態は次のように定まる。配信対象へのコピーが済んだ後に受け入れ前の片付けが失敗しても、
 * 公開キーは確定済みであるため再確定は拒まれ、配信される実体は変わらない。残った受け入れ前の実体は保管先のライフサイクルで
 * 期限切れになる（{@code docs/DECISIONS.md} 18）。応答がクライアントへ届かずに確定が再送された場合も同じ経路をたどる。
 * 同一キーの確定が同時に走った場合は、確定済みの判定をすり抜けた側もコピーの条件で弾かれるため、先に確定した実体だけが配信される。
 * </p>
 *
 * <p>
 * DBを触らないため {@code @WithTransaction} は付与しない。
 * </p>
 */
@ApplicationScoped
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class ConfirmAssetUploadService implements CommandService<ConfirmAssetUploadInput, ConfirmAssetUploadOutput> {

    private final AssetStorage assetStorage;
    private final long maxBytes;
    private final String publicBasePath;

    /**
     * @param assetStorage
     *            アセット保管先
     * @param maxBytes
     *            許容する最大バイト数（{@code abservice.assets.max-bytes}）
     * @param publicBasePath
     *            公開配信URLのベースパス（{@code abservice.assets.public-base-path}）
     */
    public ConfirmAssetUploadService(
            AssetStorage assetStorage,
            @ConfigProperty(name = "abservice.assets.max-bytes") long maxBytes,
            @ConfigProperty(name = "abservice.assets.public-base-path") String publicBasePath) {
        this.assetStorage = assetStorage;
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
     * 確定をやり直しても置き換わらない（#285）。
     */
    private Uni<ConfirmAssetUploadOutput> confirmUnlessPublished(String assetKey, boolean published) {
        return published
                ? Uni.createFrom().failure(alreadyPublished(assetKey))
                : inspectAndConfirm(assetKey);
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
                .onFailure(AssetChangedDuringConfirmException.class)
                .transform(cause -> changedDuringConfirm(assetKey, cause))
                .replaceWith(
                        () -> new ConfirmAssetUploadOutput(
                                assetKey,
                                publicBasePath + "/" + assetKey,
                                format.contentType(),
                                stored.totalBytes()));
    }

    /**
     * 確定済みの公開キーをもう一度確定しようとした場合の競合。やり直しは別のキーを払い出して行う。
     */
    private static BusinessRuleViolationException alreadyPublished(String assetKey) {
        return new BusinessRuleViolationException(
                "このアセットは確定済みです。別のキーを払い出してからアップロードしてください: key=" + assetKey);
    }

    /**
     * 検査した実体が確定までの間に置き換わっていた場合の競合。保管先の事情を、呼び出し側が読み取れる競合へ翻訳する。
     */
    private static BusinessRuleViolationException changedDuringConfirm(String assetKey, Throwable cause) {
        return new BusinessRuleViolationException(
                "検査した実体が確定までの間に置き換わりました。アップロードからやり直してください: key=" + assetKey,
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
