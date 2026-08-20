package com.abservice.application.query.albumarticle;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumArticleDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事詳細照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link AlbumArticleDataSource}
 * で直接読み取り、 {@link GetAlbumArticleResult} を返します。未存在は例外ではなく
 * {@link GetAlbumArticleResult.NotFound} として返します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class GetAlbumArticleService implements QueryService<GetAlbumArticleQuery, GetAlbumArticleResult> {

    private final AlbumArticleDataSource dataSource;

    @WithSession
    @Override
    public Uni<GetAlbumArticleResult> query(GetAlbumArticleQuery query) {
        return dataSource.findByDomainId(query.albumId())
                .map(GetAlbumArticleService::toResult);
    }

    static GetAlbumArticleResult toResult(@Nullable AlbumArticleTableRecord entity) {
        return Optional.ofNullable(entity)
                .map(AlbumArticleViewMapper::toView)
                .<GetAlbumArticleResult>map(GetAlbumArticleResult.Found::new)
                .orElseGet(GetAlbumArticleResult.NotFound::new);
    }
}
