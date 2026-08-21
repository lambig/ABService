package com.abservice.application.query.album;

import com.abservice.application.query.AudienceVisibility;
import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
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
                .map(entity -> toResult(entity, assetBasePath));
    }

    static GetAlbumResult toResult(@Nullable AlbumTableRecord entity, String assetBasePath) {
        return Optional.ofNullable(entity)
                .map(found -> AlbumViewMapper.toView(found, assetBasePath))
                .<GetAlbumResult>map(GetAlbumResult.Found::new)
                .orElseGet(GetAlbumResult.NotFound::new);
    }
}
