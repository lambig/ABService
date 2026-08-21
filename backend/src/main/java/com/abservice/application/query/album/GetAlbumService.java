package com.abservice.application.query.album;

import com.abservice.application.query.AudienceVisibility;
import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.datasource.AlbumExternalAudioRow;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

/**
 * アルバム詳細照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link AlbumDataSource} で直接読み取り、
 * {@link GetAlbumResult} を返します。未存在は例外ではなく {@link GetAlbumResult.NotFound}
 * として返します。対象範囲はクエリの {@code audience} が決め、公開向け（{@code PUBLIC}）では下書き（未公開）
 * アルバムを未存在として扱い、管理向け（{@code ADMIN}）では下書きも返します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class GetAlbumService implements QueryService<GetAlbumQuery, GetAlbumResult> {

    private final AlbumDataSource dataSource;

    /** アセットの配信ベースパス（カバー画像の配信URL組み立てに使う） */
    @ConfigProperty(name = "abservice.assets.public-base-path")
    private final String assetBasePath;

    @WithSession
    @Override
    public Uni<GetAlbumResult> query(GetAlbumQuery query) {
        return dataSource.findByDomainId(query.albumId(), AudienceVisibility.of(query.audience()))
                .flatMap(this::toResultWithExternalAudios);
    }

    /**
     * 外部音源はアルバム本体とは別クエリで取得して結果に載せる。
     *
     * <p>
     * 対象アルバムが無い場合は外部音源を引かずに {@link GetAlbumResult.NotFound} を返す。
     * </p>
     *
     * @param entity
     *            照会したアルバムエンティティ（対象外・未存在の場合はnull）
     * @return 照会結果
     */
    private Uni<GetAlbumResult> toResultWithExternalAudios(@Nullable AlbumTableRecord entity) {
        return Optional.ofNullable(entity)
                .map(
                        found -> dataSource.findExternalAudiosByAlbumIds(List.of(found.getAlbumId()))
                                .map(
                                        externalAudios -> toResult(
                                                found,
                                                assetBasePath,
                                                externalAudios)))
                .orElseGet(() -> Uni.createFrom().item(new GetAlbumResult.NotFound()));
    }

    static GetAlbumResult toResult(
            @Nullable AlbumTableRecord entity,
            String assetBasePath,
            List<AlbumExternalAudioRow> externalAudios) {
        return Optional.ofNullable(entity)
                .map(
                        found -> AlbumViewMapper.toView(
                                found,
                                assetBasePath,
                                externalAudios))
                .<GetAlbumResult>map(GetAlbumResult.Found::new)
                .orElseGet(GetAlbumResult.NotFound::new);
    }
}
