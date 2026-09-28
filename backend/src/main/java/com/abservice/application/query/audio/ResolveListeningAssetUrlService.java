package com.abservice.application.query.audio;

import com.abservice.application.audio.AudioApiValues;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.PresignedDownload;
import com.abservice.application.port.PrivateAudioDownloads;
import com.abservice.application.query.QueryService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.infrastructure.persistence.datasource.ListeningPackageDataSource;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 認証した端末へ、現在の配布パッケージに含まれる音源の取得URLを期限付きで解決する。
 *
 * <p>
 * 対象は公開済み作品の確定クロスフェードとして関連付いた音源だけ。関連付けが外れた音源・未確定・未存在は区別せず未存在として
 * 拒む（端末に登録の有無を教えない）。URLの期限は端末の資格情報の期限を超えない。既に発行したURLは期限まで有効で、 端末は
 * packageVersion の一致で整合を取る。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND})
public class ResolveListeningAssetUrlService
        implements
            QueryService<ResolveListeningAssetUrlService.Query, ResolveListeningAssetUrlService.Result> {
    private final PrivateAudioAccess access;
    private final ListeningPackageDataSource packages;
    private final PrivateAudioDownloads downloads;

    @Override
    public Uni<Result> query(Query query) {
        return Uni.createFrom().item(() -> {
            access.requireEnabled();
            return AudioApiValues.uuid(query.assetId(), "assetId");
        })
                .chain(this::requireDistributed)
                .map(assetId -> new Result(assetId, downloads.presign(assetId, query.credentialExpiresAt())));
    }

    private Uni<UUID> requireDistributed(UUID assetId) {
        return packages.distributes(assetId)
                .invoke(
                        distributed -> Optional.of(distributed)
                                .filter(Boolean::booleanValue)
                                .orElseThrow(() -> EntityNotFoundException.of("ListeningAsset", assetId.toString())))
                .replaceWith(assetId);
    }

    /**
     * @param assetId
     *            Manifest の assetId（音源の登録ID）
     * @param credentialExpiresAt
     *            要求した端末の資格情報の期限。URLはこれを超えない
     */
    public record Query(@Nullable String assetId, Instant credentialExpiresAt) implements QueryService.Query {
    }

    public record Result(UUID assetId, PresignedDownload download) implements QueryService.Result {
    }
}
