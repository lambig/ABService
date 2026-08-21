package com.abservice.application.query.album;

import com.abservice.application.query.AudienceVisibility;
import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * アルバム一覧照会サービス（ページネーション付き）
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link AlbumDataSource} が返す
 * {@code PanacheQuery} の {@code list()}/{@code count()}/{@code pageCount()}
 * をそのまま活用し、 独自の COUNT クエリやページ数計算式を書かない。対象範囲はクエリの {@code audience}
 * が決め、公開向け（{@code PUBLIC}）では下書き（未公開）アルバムを一覧に含めず、管理向け（{@code ADMIN}） では下書きも含めます。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ListAlbumsService implements QueryService<ListAlbumsQuery, ListAlbumsResult> {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AlbumDataSource dataSource;

    /** アセットの配信ベースパス（カバー画像の配信URL組み立てに使う） */
    @ConfigProperty(name = "abservice.assets.public-base-path")
    private final String assetBasePath;

    @WithSession
    @Override
    public Uni<ListAlbumsResult> query(ListAlbumsQuery query) {
        final var page = clampPage(query.page());
        final var size = clampSize(query.size());
        final var panacheQuery = dataSource.pagedQuery(
                page,
                size,
                AudienceVisibility.of(query.audience()));
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
                                size,
                                assetBasePath));
    }

    static ListAlbumsResult toResult(
            Tuple3<List<AlbumTableRecord>, Long, Integer> tuple,
            int page,
            int size,
            String assetBasePath) {
        return new ListAlbumsResult(
                tuple.getItem1().stream().map(entity -> AlbumViewMapper.toView(entity, assetBasePath)).toList(),
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
