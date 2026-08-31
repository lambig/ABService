package com.abservice.application.query.album;

import com.abservice.application.query.AudienceVisibility;
import com.abservice.application.query.QueryService;
import com.abservice.application.query.SortKeys;
import com.abservice.application.query.album.model.AlbumView;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.datasource.AlbumExternalAudioRow;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

/**
 * アルバム一覧照会サービス（ページネーション付き）
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link AlbumDataSource} が返す
 * {@code PanacheQuery} の {@code list()}/{@code count()}/{@code pageCount()}
 * をそのまま活用し、 独自の COUNT クエリやページ数計算式を書かない。対象範囲はクエリの {@code audience}
 * が決め、公開向け（{@code PUBLIC}）では下書き（未公開）アルバムを一覧に含めず、管理向け（{@code ADMIN}） では下書きも含めます。
 * </p>
 *
 * <p>
 * タイトル・カタログナンバーでの絞り込みは、指定されたときだけ条件に加わります（空文字・空白のみは未指定として
 * 扱う。{@link #keywordOrNull}）。いまこれを使うのは記事編集画面
 * （紐付け先アルバムを検索して選ぶ）であり、公開向けのエンドポイントは値を渡しません。要求元によらず同じ絞り込みが
 * 使える形にしてあるため、公開サイトに作品検索を置く判断が出た場合はエンドポイント側で受け取るだけで足ります。
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
                AudienceVisibility.of(query.audience()),
                SortKeys.resolve(
                        AlbumSortKey.values(),
                        query.sort(),
                        query.direction(),
                        query.audience()),
                keywordOrNull(query.title()),
                keywordOrNull(query.catalogNumber()));
        return Uni.combine().all()
                .unis(
                        panacheQuery.list(),
                        panacheQuery.count(),
                        panacheQuery.pageCount())
                .asTuple()
                .flatMap(
                        tuple -> toResultWithExternalAudios(
                                tuple,
                                page,
                                size));
    }

    /**
     * 外部音源はアルバム本体とは別クエリで取得して結果に載せる。
     *
     * <p>
     * ページ内のアルバムをまとめて1クエリで引き、アルバムごとに振り分ける（アルバム件数分のクエリを発行しない）。
     * </p>
     *
     * @param tuple
     *            ページ内のアルバム・総件数・総ページ数
     * @param page
     *            ページ番号（0始まり）
     * @param size
     *            1ページの件数
     * @return 一覧照会結果
     */
    private Uni<ListAlbumsResult> toResultWithExternalAudios(
            Tuple3<List<AlbumTableRecord>, Long, Integer> tuple,
            int page,
            int size) {
        return dataSource.findExternalAudiosByAlbumIds(albumIds(tuple.getItem1()))
                .map(
                        externalAudios -> toResult(
                                tuple,
                                page,
                                size,
                                assetBasePath,
                                groupByAlbumId(externalAudios)));
    }

    private static List<Long> albumIds(List<AlbumTableRecord> albums) {
        return albums.stream().map(AlbumTableRecord::getAlbumId).toList();
    }

    static Map<Long, List<AlbumExternalAudioRow>> groupByAlbumId(List<AlbumExternalAudioRow> externalAudios) {
        return externalAudios.stream().collect(Collectors.groupingBy(AlbumExternalAudioRow::albumId));
    }

    static ListAlbumsResult toResult(
            Tuple3<List<AlbumTableRecord>, Long, Integer> tuple,
            int page,
            int size,
            String assetBasePath,
            Map<Long, List<AlbumExternalAudioRow>> externalAudiosByAlbumId) {
        return new ListAlbumsResult(
                toViews(
                        tuple.getItem1(),
                        assetBasePath,
                        externalAudiosByAlbumId),
                page,
                size,
                tuple.getItem2(),
                tuple.getItem3());
    }

    private static List<AlbumView> toViews(
            List<AlbumTableRecord> albums,
            String assetBasePath,
            Map<Long, List<AlbumExternalAudioRow>> externalAudiosByAlbumId) {
        return albums.stream()
                .map(
                        entity -> AlbumViewMapper.toView(
                                entity,
                                assetBasePath,
                                externalAudiosByAlbumId.getOrDefault(entity.getAlbumId(), List.of()),
                                List.of()))
                .toList();
    }

    static int clampPage(int page) {
        return Math.max(page, 0);
    }

    static int clampSize(int size) {
        return size < 1
                ? DEFAULT_SIZE
                : Math.min(size, MAX_SIZE);
    }

    /**
     * 空文字・空白のみの検索語を未指定として扱う。
     *
     * <p>
     * 検索フォームの各値を常にクエリパラメータへ載せる実装では、未入力が空文字として届く。これをそのまま条件にすると {@code like '%%'}
     * が積に加わり、対象の列が null の行だけが結果から落ちる（SQL の {@code NULL LIKE '%%'} は真ではなく NULL
     * のため）。カタログナンバーは未付与がありうるので、絞り込んでいないつもりでカタログナンバーを 持たないアルバムが消えることになる。
     * </p>
     *
     * @param keyword
     *            クエリパラメータで指定された検索語（nullable）
     * @return 語として意味を持つ場合はその値、未指定・空文字・空白のみなら null
     */
    static @Nullable String keywordOrNull(@Nullable String keyword) {
        return Optional.ofNullable(keyword)
                .filter(Predicate.not(String::isBlank))
                .orElse(null);
    }
}
