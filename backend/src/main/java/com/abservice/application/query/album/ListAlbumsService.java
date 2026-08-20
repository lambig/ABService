package com.abservice.application.query.album;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.datasource.Visibility;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * アルバム一覧照会サービス（ページネーション付き）
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link AlbumDataSource} が返す
 * {@code PanacheQuery} の {@code list()}/{@code count()}/{@code pageCount()}
 * をそのまま活用し、 独自の COUNT クエリやページ数計算式を書かない。本サービスは認証を伴わない公開向けQueryのため、
 * 下書き（未公開）アルバムは一覧に含めません。下書きを含めた閲覧は認証必須の別経路で提供します（#116）。
 * </p>
 */
@ApplicationScoped
public class ListAlbumsService implements QueryService<ListAlbumsQuery, ListAlbumsResult> {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AlbumDataSource dataSource;

    /**
     * @param dataSource
     *            アルバムデータソース（Read）
     */
    public ListAlbumsService(AlbumDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @WithSession
    @Override
    public Uni<ListAlbumsResult> query(ListAlbumsQuery query) {
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

    static ListAlbumsResult toResult(
            Tuple3<List<AlbumTableRecord>, Long, Integer> tuple,
            int page,
            int size) {
        return new ListAlbumsResult(
                tuple.getItem1().stream().map(AlbumViewMapper::toView).toList(),
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
