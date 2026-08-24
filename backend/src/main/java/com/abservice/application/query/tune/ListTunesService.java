package com.abservice.application.query.tune;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;
import com.abservice.application.query.SortKeys;
import com.abservice.infrastructure.persistence.datasource.TuneDataSource;
import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.AllArgsConstructor;

/**
 * チューン一覧照会サービス（ページネーション付き）
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link TuneDataSource} が返す
 * {@code PanacheQuery} の {@code list()}/{@code count()}/{@code pageCount()}
 * をそのまま活用し、 独自の COUNT クエリやページ数計算式を書かない。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ListTunesService implements QueryService<ListTunesQuery, ListTunesResult> {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final TuneDataSource dataSource;

    @WithSession
    @Override
    public Uni<ListTunesResult> query(ListTunesQuery query) {
        final var page = clampPage(query.page());
        final var size = clampSize(query.size());
        final var panacheQuery = dataSource.pagedQuery(
                page,
                size,
                SortKeys.resolve(
                        TuneSortKey.values(),
                        query.sort(),
                        query.direction(),
                        Audience.ADMIN));
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

    static ListTunesResult toResult(
            Tuple3<List<TuneTableRecord>, Long, Integer> tuple,
            int page,
            int size) {
        return new ListTunesResult(
                tuple.getItem1().stream().map(TuneViewMapper::toView).toList(),
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
