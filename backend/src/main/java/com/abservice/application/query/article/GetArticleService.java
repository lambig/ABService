package com.abservice.application.query.article;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.ArticleDataSource;
import com.abservice.infrastructure.persistence.entity.ArticleEntity;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 記事詳細照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link ArticleDataSource} で直接読み取り、
 * {@link GetArticleResult} を返します。未存在は例外ではなく {@link GetArticleResult.NotFound}
 * として返します。
 * </p>
 */
@ApplicationScoped
public class GetArticleService implements QueryService<GetArticleQuery, GetArticleResult> {

    private final ArticleDataSource dataSource;

    /**
     * @param dataSource
     *            記事データソース（Read）
     */
    public GetArticleService(ArticleDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @WithSession
    @Override
    public Uni<GetArticleResult> query(GetArticleQuery query) {
        return dataSource.findByDomainId(query.articleId())
                .map(GetArticleService::toResult);
    }

    static GetArticleResult toResult(@Nullable ArticleEntity entity) {
        return Optional.ofNullable(entity)
                .map(ArticleViewMapper::toView)
                .<GetArticleResult>map(GetArticleResult.Found::new)
                .orElseGet(GetArticleResult.NotFound::new);
    }
}
