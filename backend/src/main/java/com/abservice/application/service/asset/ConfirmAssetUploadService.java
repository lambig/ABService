package com.abservice.application.service.asset;

import com.abservice.application.port.AssetStorage;
import com.abservice.application.port.StoredAssetHead;
import com.abservice.application.service.CommandService;
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
 * 確定は受け入れ前の場所から配信対象へ実体を移す操作であり、クライアントが書き込めるのは受け入れ前だけ。署名付きURLの
 * 有効期限内に同じURLへ再度アップロードされても、確定済みの配信実体は変わらない（検査した実体と配信される実体がずれない）。
 * </p>
 *
 * <p>
 * DBを触らないため {@code @WithTransaction} は付与しない。
 * </p>
 */
@ApplicationScoped
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
        return assetStorage.readHead(input.assetKey(), AssetImageFormat.REQUIRED_PREFIX_BYTES)
                .flatMap(head -> verify(input.assetKey(), head));
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
        return assetStorage.publish(assetKey)
                .replaceWith(
                        () -> new ConfirmAssetUploadOutput(
                                assetKey,
                                publicBasePath + "/" + assetKey,
                                format.contentType(),
                                stored.totalBytes()));
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
