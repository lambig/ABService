package com.abservice.application.service.asset;

import com.abservice.application.port.AssetStorage;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.EntityId;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * アップロードURL発行ユースケース
 *
 * <p>
 * 受け入れ可能な Content-Type を確認したうえでアセットキーを払い出し、そのキーへのアップロードだけを許可する署名付き
 * URLを返す。実体はクライアントから保管先へ直接送られるため、本サービスはバイト列を扱わない。実体の検査は
 * {@link ConfirmAssetUploadService} が行う。
 * </p>
 *
 * <p>
 * DBを触らないため {@code @WithTransaction} は付与しない。
 * </p>
 */
@ApplicationScoped
public class IssueAssetUploadUrlService
        implements
            CommandService<IssueAssetUploadUrlInput, IssueAssetUploadUrlOutput> {

    private final AssetStorage assetStorage;
    private final long maxBytes;

    /**
     * @param assetStorage
     *            アセット保管先
     * @param maxBytes
     *            アップロードを許容する最大バイト数（{@code abservice.assets.max-bytes}）
     */
    public IssueAssetUploadUrlService(
            AssetStorage assetStorage,
            @ConfigProperty(name = "abservice.assets.max-bytes") long maxBytes) {
        this.assetStorage = assetStorage;
        this.maxBytes = maxBytes;
    }

    @Override
    public Uni<IssueAssetUploadUrlOutput> execute(IssueAssetUploadUrlInput input) {
        return AssetImageFormat.ofContentType(input.contentType())
                .map(this::issueFor)
                .orElseGet(() -> Uni.createFrom().failure(unsupportedContentType(input.contentType())));
    }

    private Uni<IssueAssetUploadUrlOutput> issueFor(AssetImageFormat format) {
        final var assetKey = EntityId.generateUuidV7() + "." + format.extension();
        return assetStorage.presignUpload(assetKey, format.contentType())
                .map(
                        presigned -> new IssueAssetUploadUrlOutput(
                                assetKey,
                                presigned.url(),
                                presigned.expiresAt(),
                                maxBytes));
    }

    private static ValidationException unsupportedContentType(String contentType) {
        return new ValidationException(
                List.of(
                        new ErrorResult(
                                "contentType",
                                "対応していない形式です: " + contentType + "（対応: "
                                        + AssetImageFormat.acceptedContentTypes() + "）",
                                "UNSUPPORTED_CONTENT_TYPE")));
    }
}
