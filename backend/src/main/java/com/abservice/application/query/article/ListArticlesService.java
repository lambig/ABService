package com.abservice.application.query.article;

import com.abservice.application.query.AudienceVisibility;
import com.abservice.application.query.QueryService;
import com.abservice.application.query.SortKeys;
import com.abservice.infrastructure.persistence.datasource.ArticleDataSource;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.AllArgsConstructor;

/**
 * 記事一覧照会サービス（ページネーション付き）
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず、{@link ArticleDataSource} が返す
 * {@code PanacheQuery} の {@code list()}/{@code count()}/{@code pageCount()}
 * をそのまま活用し、 独自の COUNT クエリやページ数計算式を書かない。対象範囲はクエリの {@code audience}
 * が決め、公開向け（{@code PUBLIC}）では非公開（下書き）記事を一覧に含めず、管理向け（{@code ADMIN}） では下書きも含めます。
 * </p>
 *
 * <p>
 * 参照先アルバムでの絞り込みと公開状態での絞り込みは、指定されたときだけ条件に加わります。これらを使うのは管理画面
 * （カスケードの影響範囲の事前確認）であり、公開向けのエンドポイントは値を渡しません。削除は参照記事すべてを、非公開化は
 * そのうち公開中のものだけを対象にするため、後者は公開状態の絞り込みと組み合わせて引きます。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ListArticlesService implements QueryService<ListArticlesQuery, ListArticlesResult> {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ArticleDataSource dataSource;

    @WithSession
    @Override
    public Uni<ListArticlesResult> query(ListArticlesQuery query) {
        final var page = clampPage(query.page());
        final var size = clampSize(query.size());
        final var panacheQuery = dataSource.pagedQuery(
                page,
                size,
                AudienceVisibility.of(query.audience()),
                SortKeys.resolve(
                        ArticleSortKey.values(),
                        query.sort(),
                        query.direction(),
                        query.audience()),
                query.albumId(),
                query.publicFlag());
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
