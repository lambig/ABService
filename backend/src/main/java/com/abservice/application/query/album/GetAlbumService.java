package com.abservice.application.query.album;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * アルバム詳細照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link AlbumDataSource} で直接読み取り、
 * {@link GetAlbumResult} を返します。未存在は例外ではなく {@link GetAlbumResult.NotFound}
 * として返します。
 * </p>
 */
@ApplicationScoped
public class GetAlbumService implements QueryService<GetAlbumQuery, GetAlbumResult> {

    private final AlbumDataSource dataSource;

    /**
     * @param dataSource
     *            アルバムデータソース（Read）
     */
    public GetAlbumService(AlbumDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @WithSession
    @Override
    public Uni<GetAlbumResult> query(GetAlbumQuery query) {
        return dataSource.findByDomainId(query.albumId())
                .map(GetAlbumService::toResult);
    }

    static GetAlbumResult toResult(@Nullable AlbumTableRecord entity) {
        return Optional.ofNullable(entity)
                .map(AlbumViewMapper::toView)
                .<GetAlbumResult>map(GetAlbumResult.Found::new)
                .orElseGet(GetAlbumResult.NotFound::new);
    }
}
