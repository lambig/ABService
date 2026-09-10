package com.abservice.application.query.article;

import com.abservice.application.query.AudienceVisibility;
import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.ArticleDataSource;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 記事詳細照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link ArticleDataSource} で直接読み取り、
 * {@link GetArticleResult} を返します。未存在は例外ではなく {@link GetArticleResult.NotFound}
 * として返します。対象範囲はクエリの {@code audience} が決め、公開向け（{@code PUBLIC}）では非公開（下書き）
 * 記事を未存在として扱い、管理向け（{@code ADMIN}）では下書きも返します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class GetArticleService implements QueryService<GetArticleQuery, GetArticleResult> {

    private final ArticleDataSource dataSource;

    @WithSession
    @Override
    public Uni<GetArticleResult> query(GetArticleQuery query) {
        return dataSource.findByDomainId(query.articleId(), AudienceVisibility.of(query.audience()))
                .map(GetArticleService::toResult);
    }

    static GetArticleResult toResult(@Nullable ArticleTableRecord entity) {
        return Optional.ofNullable(entity)
                .map(ArticleViewMapper::toDetailView)
                .<GetArticleResult>map(GetArticleResult.Found::new)
                .orElseGet(GetArticleResult.NotFound::new);
    }
}
