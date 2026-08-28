package com.abservice.infrastructure.persistence.datasource;

import com.abservice.application.query.SortSpec;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Article DataSource (DAO)
 *
 * <p>
 * Panacheを使用した記事データアクセス層。
 * </p>
 */
@ApplicationScoped
public class ArticleDataSource implements PanacheRepositoryBase<ArticleTableRecord, Long> {

    private static final String EAGER_SELECT = "SELECT DISTINCT a FROM ArticleTableRecord a "
            + "LEFT JOIN FETCH a.articleTagLinks link "
            + "LEFT JOIN FETCH link.articleTag "
            + "LEFT JOIN FETCH a.albumReference ";

    private static final String WHERE_DOMAIN_ID = "WHERE a.domainId = :domainId";

    private static final String WHERE_DOMAIN_ID_PUBLIC = WHERE_DOMAIN_ID + " AND a.isPublic = true";

    /** 一覧の絞り込み条件（Panache のクエリ断片。エンティティ別名を伴わない） */
    private static final String WHERE_PUBLIC = "isPublic = true";

    private static final String WHERE_ALBUM = "albumReference.albumId = :albumId";

    private static final String WHERE_PUBLIC_FLAG = "isPublic = :publicFlag";

    private final Mutiny.SessionFactory sessionFactory;

    public ArticleDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * ドメインIDで記事を検索（タグを含む）
     *
     * <p>
     * {@code visibility}に{@link Visibility#PUBLIC_ONLY}を渡すと、公開フラグ=trueの記事のみを対象にする
     * （公開向けQuery {@code GetArticleService}専用。非公開記事は存在しないものとして{@code null}を返す）。
     * </p>
     *
     * @param domainId
     *            ドメインID
     * @param visibility
     *            検索対象の公開状態スコープ
     * @return 該当する記事（対象外・未存在の場合はnull）
     */
    public Uni<ArticleTableRecord> findByDomainId(
            String domainId,
            Visibility visibility) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                EAGER_SELECT
                                        + (visibility == Visibility.PUBLIC_ONLY
                                                ? WHERE_DOMAIN_ID_PUBLIC
                                                : WHERE_DOMAIN_ID),
                                ArticleTableRecord.class)
                        .setParameter("domainId", domainId).getSingleResultOrNull());
    }

    /**
     * 記事を全件取得（タグを含む）
     *
     * @return 記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findAllEager() {
        return sessionFactory.withSession(
                session -> session.createQuery(EAGER_SELECT, ArticleTableRecord.class)
                        .getResultList());
    }

    /**
     * 複数のドメインIDで記事を一括検索（タグを含む）
     *
     * @param domainIds
     *            記事のドメインID群
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByIds(Collection<String> domainIds) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.domainId IN :domainIds", ArticleTableRecord.class)
                        .setParameter("domainIds", domainIds).getResultList());
    }

    /**
     * 記事タイプで記事を検索（タグを含む）
     *
     * @param articleType
     *            記事タイプ
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByArticleType(String articleType) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.articleType = :articleType", ArticleTableRecord.class)
                        .setParameter("articleType", articleType).getResultList());
    }

    /**
     * アルバムIDで記事を検索（タグを含む）
     *
     * <p>
     * 同一アルバムは複数の記事から参照されうる（{@code article.album_id} に一意制約はない）。
     * </p>
     *
     * @param albumId
     *            アルバムID (domain_id)
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByAlbumId(String albumId) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                EAGER_SELECT + "WHERE a.albumReference.albumId = :albumId",
                                ArticleTableRecord.class)
                        .setParameter("albumId", albumId).getResultList());
    }

    /**
     * 公開フラグで記事を検索（タグを含む）
     *
     * @param publicFlag
     *            公開フラグ
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByPublicFlag(boolean publicFlag) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.isPublic = :publicFlag", ArticleTableRecord.class)
                        .setParameter("publicFlag", publicFlag).getResultList());
    }

    /**
     * 公開日の範囲で記事を検索（タグを含む）
     *
     * @param startDate
     *            開始日時
     * @param endDate
     *            終了日時
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByPublishedAtBetween(Instant startDate, Instant endDate) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE a.publishedAt >= :startDate AND a.publishedAt <= :endDate",
                        ArticleTableRecord.class).setParameter("startDate", startDate).setParameter("endDate", endDate)
                        .getResultList());
    }

    /**
     * タイトルで記事を検索（部分一致、タグを含む）
     *
     * @param titleKeyword
     *            タイトルキーワード
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByTitleContaining(String titleKeyword) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.title LIKE :keyword", ArticleTableRecord.class)
                        .setParameter("keyword", "%" + titleKeyword + "%").getResultList());
    }

    /**
     * ページ指定で記事を検索（一覧表示用・タグは含まない）
     *
     * <p>
     * 一覧表示の Read
     * Model（{@link com.abservice.application.query.article.model.ArticleView}）は
     * タグを含まないフラットDTOのため、JOIN FETCHを伴わない単純なページングで問題ない （JOIN
     * FETCH併用時のページング崩れを回避できる）。件数・総ページ数は返された {@link PanacheQuery} 自身の
     * {@code count()}/{@code pageCount()} から取得する。{@code visibility}に
     * {@link Visibility#PUBLIC_ONLY}を渡すと、公開向けQuery（{@code ListArticlesService}）専用として
     * {@code isPublic = true}の記事のみを対象にする。
     * </p>
     *
     * @param page
     *            ページ番号（0始まり）
     * @param size
     *            1ページの件数
     * @param visibility
     *            検索対象の公開状態スコープ
     * @param sort
     *            解決済みの並び順
     * @param albumId
     *            参照先アルバムでの絞り込み（nullable。未指定なら絞り込まない）
     * @param publicFlag
     *            公開状態での絞り込み（nullable。未指定なら絞り込まない）
     * @return ページングクエリ
     */
    public PanacheQuery<ArticleTableRecord> pagedQuery(
            int page,
            int size,
            Visibility visibility,
            SortSpec sort,
            @Nullable String albumId,
            @Nullable Boolean publicFlag) {
        return conditionsOf(
                visibility,
                albumId,
                publicFlag).stream()
                .reduce((left, right) -> left + " AND " + right)
                .map(
                        where -> find(
                                where,
                                SortOrders.of(sort),
                                parametersOf(albumId, publicFlag)))
                .orElseGet(() -> findAll(SortOrders.of(sort)))
                .page(Page.of(page, size));
    }

    private static List<String> conditionsOf(
            Visibility visibility,
            @Nullable String albumId,
            @Nullable Boolean publicFlag) {
        return Stream.of(
                conditionIf(visibility == Visibility.PUBLIC_ONLY, WHERE_PUBLIC),
                conditionIf(albumId != null, WHERE_ALBUM),
                conditionIf(publicFlag != null, WHERE_PUBLIC_FLAG))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<String> conditionIf(boolean applies, String condition) {
        return applies
                ? Optional.of(condition)
                : Optional.empty();
    }

    private static Map<String, Object> parametersOf(@Nullable String albumId, @Nullable Boolean publicFlag) {
        return Stream.of(
                parameterIf("albumId", albumId),
                parameterIf("publicFlag", publicFlag))
                .flatMap(Optional::stream)
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue));
    }

    private static Optional<Map.Entry<String, Object>> parameterIf(String name, @Nullable Object value) {
        return Optional.ofNullable(value)
                .map(present -> Map.entry(name, present));
    }

    /**
     * 記事IDで削除
     *
     * @param id
     *            記事ID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByArticleId(String domainId) {
        return delete("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * 複数のドメインIDで記事を一括削除
     *
     * @param domainIds
     *            記事のドメインID群
     * @return 完了シグナル
     */
    public Uni<Void> deleteByArticleIds(Collection<String> domainIds) {
        return delete("domainId in ?1", domainIds).replaceWithVoid();
    }

    /**
     * 記事IDで記事が存在するか確認
     *
     * @param id
     *            記事ID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByArticleId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }
}
