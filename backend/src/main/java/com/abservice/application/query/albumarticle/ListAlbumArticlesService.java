package com.abservice.application.query.albumarticle;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumArticleDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.AllArgsConstructor;

/**
 * アルバム記事一覧照会サービス（ページネーション付き）
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link AlbumArticleDataSource} が返す
 * {@code PanacheQuery} の {@code list()}/{@code count()}/{@code pageCount()}
 * をそのまま活用し、 独自の COUNT クエリやページ数計算式を書かない。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ListAlbumArticlesService implements QueryService<ListAlbumArticlesQuery, ListAlbumArticlesResult> {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AlbumArticleDataSource dataSource;

    @WithSession
    @Override
    public Uni<ListAlbumArticlesResult> query(ListAlbumArticlesQuery query) {
        final var page = clampPage(query.page());
        final var size = clampSize(query.size());
        final var panacheQuery = dataSource.pagedQuery(page, size);
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

    static ListAlbumArticlesResult toResult(
            Tuple3<List<AlbumArticleTableRecord>, Long, Integer> tuple,
            int page,
            int size) {
        return new ListAlbumArticlesResult(
                tuple.getItem1().stream().map(AlbumArticleViewMapper::toView).toList(),
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
