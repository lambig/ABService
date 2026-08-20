package com.abservice.application.query.article;

import com.abservice.infrastructure.persistence.datasource.ArticleDataSource;
import com.abservice.infrastructure.persistence.datasource.Visibility;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import com.abservice.application.query.QueryService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * 記事一覧照会サービス（ページネーション付き）
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link ArticleDataSource} が返す
 * {@code PanacheQuery} の {@code list()}/{@code count()}/{@code pageCount()}
 * をそのまま活用し、 独自の COUNT クエリやページ数計算式を書かない。本サービスは認証を伴わない公開向けQueryのため、
 * 非公開（下書き）記事は一覧に含めません。下書きを含めた閲覧は認証必須の別経路で提供します（#116）。
 * </p>
 */
@ApplicationScoped
public class ListArticlesService implements QueryService<ListArticlesQuery, ListArticlesResult> {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ArticleDataSource dataSource;

    /**
     * @param dataSource
     *            記事データソース（Read）
     */
    public ListArticlesService(ArticleDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @WithSession
    @Override
    public Uni<ListArticlesResult> query(ListArticlesQuery query) {
        final var page = clampPage(query.page());
        final var size = clampSize(query.size());
        final var panacheQuery = dataSource.pagedQuery(
                page,
                size,
                Visibility.PUBLIC_ONLY);
        return Uni.combine().all()
                .unis(
                        panacheQuery.list(),
                        panacheQuery.count(),
                        panacheQuery.pageCount())
                .asTuple()
                .map(
                        tuple -> toResult(
                                tuple,
                                page,
                                size));
    }

    static ListArticlesResult toResult(
            Tuple3<List<ArticleTableRecord>, Long, Integer> tuple,
            int page,
            int size) {
        return new ListArticlesResult(
                tuple.getItem1().stream().map(ArticleViewMapper::toView).toList(),
                page,
                size,
                tuple.getItem2(),
                tuple.getItem3());
    }

    static int clampPage(int page) {
        return Math.max(page, 0);
    }

    static int clampSize(int size) {
        return size < 1
                ? DEFAULT_SIZE
                : Math.min(size, MAX_SIZE);
    }
}
