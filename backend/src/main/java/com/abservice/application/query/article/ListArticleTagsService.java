package com.abservice.application.query.article;

import static com.abservice.lib.Iterables.toList;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.article.model.ArticleTagView;
import com.abservice.infrastructure.persistence.datasource.ArticleTagDataSource;
import com.abservice.infrastructure.persistence.entity.ArticleTagTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 記事タグ一覧照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。管理画面が記事にタグを付けるときの選択肢として引く。表示の並びを安定させるため 名前の昇順で返す。
 * </p>
 *
 * <p>
 * 要求元による対象範囲の違いを持たない。タグ語彙そのものは公開・非公開の状態を持たないためで、公開向けに この照会を開くかは用途（v1.0
 * ではタグによる絞り込みを行わない）で決まる。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ListArticleTagsService implements QueryService<ListArticleTagsQuery, ListArticleTagsResult> {

    private final ArticleTagDataSource dataSource;

    @WithSession
    @Override
    public Uni<ListArticleTagsResult> query(ListArticleTagsQuery query) {
        return dataSource.findAllOrderByName()
                .map(toList(ListArticleTagsService::toView))
                .map(ListArticleTagsResult::new);
    }

    private static ArticleTagView toView(ArticleTagTableRecord entity) {
        return new ArticleTagView(entity.getDomainId(), entity.getName());
    }
}
