package com.abservice.infrastructure.persistence.datasource;

import com.abservice.application.query.SortSpec;
import com.abservice.infrastructure.persistence.entity.AlbumExternalAudioTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Album DataSource (DAO)
 *
 * <p>
 * Panacheを使用したアルバムデータアクセス層。 アルバムとその関連エンティティの永続化を担当する。
 * </p>
 */
@ApplicationScoped
public class AlbumDataSource implements PanacheRepositoryBase<AlbumTableRecord, Long> {

    /** 一覧の絞り込み条件（Panache のクエリ断片。エンティティ別名を伴わない） */
    private static final String WHERE_PUBLISHED = "publishedAt is not null";

    private static final String WHERE_TITLE = "lower(title) like lower(:title) escape '\\'";

    private static final String WHERE_CATALOG_NUMBER = "lower(catalogNumber) like lower(:catalogNumber) escape '\\'";

    /** LIKE のワイルドカードとして解釈させたくない文字（利用者が打った語の一部として扱う） */
    private static final Pattern LIKE_WILDCARDS = Pattern.compile("([\\\\%_])");

    private final Mutiny.SessionFactory sessionFactory;

    public AlbumDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * アルバムとその関連エンティティを永続化
     *
     * @param albumEntity
     *            アルバムエンティティ
     * @return 永続化されたアルバムエンティティ
     */
    public Uni<AlbumTableRecord> persistAlbumWithRelations(AlbumTableRecord albumEntity) {
        return persist(albumEntity).onItem()
                .transformToUni(
                        savedAlbum -> sessionFactory.withSession(
                                session -> persistChildren(
                                        session,
                                        savedAlbum,
                                        albumEntity)));
    }

    private static Uni<AlbumTableRecord> persistChildren(
            Mutiny.Session session,
            AlbumTableRecord savedAlbum,
            AlbumTableRecord albumEntity) {
        return persistChildCollection(
                session,
                savedAlbum,
                albumEntity.getTracks(),
                TrackTableRecord::setAlbum)
                .flatMap(
                        album -> persistChildCollection(
                                session,
                                album,
                                albumEntity.getExternalAudios(),
                                AlbumExternalAudioTableRecord::setAlbum));
    }

    private static <T> Uni<AlbumTableRecord> persistChildCollection(
            Mutiny.Session session,
            AlbumTableRecord savedAlbum,
            @Nullable List<T> children,
            BiConsumer<T, AlbumTableRecord> parentSetter) {
        return Optional.ofNullable(children)
                .filter(Predicate.not(List::isEmpty))
                .map(
                        items -> session
                                .persistAll(
                                        items.stream()
                                                .peek(item -> parentSetter.accept(item, savedAlbum))
                                                .toArray())
                                .replaceWith(savedAlbum))
                .orElseGet(() -> Uni.createFrom().item(savedAlbum));
    }

    /**
     * ドメインIDでアルバムを検索
     *
     * <p>
     * {@code visibility}に{@link Visibility#PUBLIC_ONLY}を渡すと、公開中（{@code publishedAt}が非null）
     * のアルバムのみを対象にする（公開向けQuery {@code GetAlbumService}専用。下書きは存在しないものとして
     * {@code null}を返す）。
     * </p>
     *
     * @param domainId
     *            アルバムのドメインID
     * @param visibility
     *            検索対象の公開状態スコープ
     * @return 該当するアルバム（対象外・未存在の場合はnull）
     */
    public Uni<AlbumTableRecord> findByDomainId(String domainId, Visibility visibility) {
        return (visibility == Visibility.PUBLIC_ONLY
                ? find("domainId = ?1 and publishedAt is not null", domainId)
                : find("domainId", domainId))
                .firstResult();
    }

    /**
     * ドメインIDでアルバム行を排他ロックして取得する
     *
     * <p>
     * ロックは現在のトランザクションが終わるまで保持され、同じ行を対象にする他のトランザクションを待たせる。集約の
     * ロード（{@link #findByIdWithTracks}）は{@code LEFT JOIN FETCH}を伴い、外部結合のnullable側には
     * {@code FOR UPDATE}を適用できないため、ロックの取得は結合を伴わないこのクエリで別途行う。子コレクションは
     * いずれもLAZYのため、ここでは結合が生成されない。
     * </p>
     *
     * @param domainId
     *            アルバムのドメインID
     * @return ロックしたアルバムエンティティ（未存在の場合はnull）
     */
    public Uni<AlbumTableRecord> lockByDomainId(String domainId) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT a FROM AlbumTableRecord a WHERE a.domainId = :domainId",
                                AlbumTableRecord.class)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .setParameter("domainId", domainId).getSingleResultOrNull());
    }

    /**
     * IDでアルバムを検索（トラック・トラック内チューン構成を含む）
     *
     * <p>
     * {@code tracks}・{@code tracks.trackTunes}・{@code externalAudios}はいずれも{@code List}（bag）のため、
     * 1クエリで複数を{@code JOIN FETCH}するとHibernateの multiple-bag-fetch
     * 制約に抵触する。{@code tracks}のみ
     * {@code JOIN FETCH}し、残りは同一セッション内で{@link Mutiny.Session#fetch}
     * により明示的に初期化する（セッション外で遅延初期化しようとすると {@code LazyInitializationException}になるため）。
     * </p>
     *
     * @param domainId
     *            アルバムのドメインID
     * @return アルバムエンティティ（トラック・トラック内チューン構成を含む）
     */
    public Uni<AlbumTableRecord> findByIdWithTracks(String domainId) {
        return sessionFactory.withSession(session -> queryByIdWithTracks(session, domainId));
    }

    private static Uni<AlbumTableRecord> queryByIdWithTracks(Mutiny.Session session, String domainId) {
        return session.createQuery(
                "SELECT DISTINCT a FROM AlbumTableRecord a " + "LEFT JOIN FETCH a.tracks "
                        + "WHERE a.domainId = :domainId",
                AlbumTableRecord.class).setParameter("domainId", domainId).getSingleResultOrNull()
                .flatMap(album -> fetchLazyChildrenOrNull(session, album));
    }

    private static Uni<AlbumTableRecord> fetchLazyChildrenOrNull(Mutiny.Session session,
            @Nullable AlbumTableRecord album) {
        return Optional.ofNullable(album)
                .map(a -> fetchLazyChildren(session, a))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    /**
     * 複数のドメインIDでアルバムを一括検索（トラック・トラック内チューン構成を含む）
     *
     * @param domainIds
     *            アルバムのドメインID群
     * @return 該当するアルバムエンティティのリスト（トラック・トラック内チューン構成を含む）
     */
    public Uni<List<AlbumTableRecord>> findByIdsWithTracks(Collection<String> domainIds) {
        return sessionFactory.withSession(session -> queryByIdsWithTracks(session, domainIds));
    }

    private static Uni<List<AlbumTableRecord>> queryByIdsWithTracks(
            Mutiny.Session session,
            Collection<String> domainIds) {
        return session.createQuery(
                "SELECT DISTINCT a FROM AlbumTableRecord a " + "LEFT JOIN FETCH a.tracks "
                        + "WHERE a.domainId IN (:domainIds)",
                AlbumTableRecord.class).setParameter("domainIds", domainIds).getResultList()
                .flatMap(albums -> fetchAllLazyChildren(session, albums));
    }

    /*
     * PERFORMANCE: 同一Mutinyセッションへの並行アクセスは内部状態を破壊する
     * （java.lang.IllegalStateException: Illegal pop() with non-matching
     * JdbcValuesSourceProcessingState）ため、transformToUniAndMergeではなく
     * transformToUniAndConcatenateで逐次実行する。
     */
    private static Uni<List<AlbumTableRecord>> fetchAllLazyChildren(Mutiny.Session session,
            List<AlbumTableRecord> albums) {
        return Multi.createFrom().iterable(albums)
                .onItem().transformToUniAndConcatenate(a -> fetchLazyChildren(session, a))
                .collect().asList();
    }

    private static Uni<AlbumTableRecord> fetchLazyChildren(Mutiny.Session session, AlbumTableRecord album) {
        return fetchTrackTunes(session, album)
                .flatMap(a -> session.fetch(a.getExternalAudios()))
                .replaceWith(album);
    }

    private static Uni<AlbumTableRecord> fetchTrackTunes(Mutiny.Session session, AlbumTableRecord album) {
        return Multi.createFrom().iterable(album.getTracks())
                .onItem().transformToUniAndConcatenate(track -> session.fetch(track.getTrackTunes()))
                .collect().asList()
                .replaceWith(album);
    }

    /**
     * 指定したアルバム（内部ID）の外部音源を表示順で取得
     *
     * <p>
     * Query側（CQRS Read）が一覧・詳細のどちらでも同じ形で使えるよう、アルバム本体とは別クエリで取得する。
     * ページング付き一覧では{@code JOIN FETCH}を併用できず（ページング崩れ）、詳細では他のコレクションと multiple-bag-fetch
     * 制約に抵触するため、ページ内のアルバムをまとめて1クエリで引く。
     * </p>
     *
     * @param albumIds
     *            アルバムの内部ID群
     * @return 該当する外部音源の投影のリスト（アルバム内部ID・表示順の昇順）
     */
    public Uni<List<AlbumExternalAudioRow>> findExternalAudiosByAlbumIds(Collection<Long> albumIds) {
        return Optional.of(albumIds)
                .filter(Predicate.not(Collection::isEmpty))
                .map(this::queryExternalAudiosByAlbumIds)
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    private Uni<List<AlbumExternalAudioRow>> queryExternalAudiosByAlbumIds(Collection<Long> albumIds) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT new com.abservice.infrastructure.persistence.datasource"
                                        + ".AlbumExternalAudioRow("
                                        + "ea.album.albumId, ea.domainId, ea.displayOrder, ea.url) "
                                        + "FROM AlbumExternalAudioTableRecord ea "
                                        + "WHERE ea.album.albumId IN (:albumIds) "
                                        + "ORDER BY ea.album.albumId, ea.displayOrder",
                                AlbumExternalAudioRow.class)
                        .setParameter("albumIds", albumIds).getResultList());
    }

    /**
     * アルバムのトラックを投影で取得する
     *
     * <p>
     * アルバム本体とは別クエリで読む（複数の {@code @OneToMany} を1クエリで JOIN FETCH すると
     * multiple-bag-fetch 制約に抵触するため）。チューン構成はさらに {@link #findTrackTunesByTrackIds}
     * で取得する。
     * </p>
     *
     * @param albumId
     *            アルバムの内部ID
     * @return 該当するトラックの投影のリスト（トラック番号の昇順）
     */
    public Uni<List<AlbumTrackRow>> findTracksByAlbumId(Long albumId) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT new com.abservice.infrastructure.persistence.datasource"
                                        + ".AlbumTrackRow("
                                        + "t.domainId, t.trackNo, t.title, t.artistDisplayName, t.artistSortKey) "
                                        + "FROM TrackTableRecord t "
                                        + "WHERE t.album.albumId = :albumId "
                                        + "ORDER BY t.trackNo",
                                AlbumTrackRow.class)
                        .setParameter("albumId", albumId).getResultList());
    }

    /**
     * トラック内のチューン構成を投影で取得する
     *
     * <p>
     * トラック群をまとめて1クエリで引き、トラックごとに振り分ける（トラック件数分のクエリを発行しない）。
     * </p>
     *
     * @param trackIds
     *            トラックのドメインID群
     * @return 該当するチューン構成の投影のリスト（トラック・登場順の昇順）
     */
    public Uni<List<AlbumTrackTuneRow>> findTrackTunesByTrackIds(Collection<String> trackIds) {
        return Optional.of(trackIds)
                .filter(Predicate.not(Collection::isEmpty))
                .map(this::queryTrackTunesByTrackIds)
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    private Uni<List<AlbumTrackTuneRow>> queryTrackTunesByTrackIds(Collection<String> trackIds) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT new com.abservice.infrastructure.persistence.datasource"
                                        + ".AlbumTrackTuneRow("
                                        + "tt.track.domainId, tt.id.seq, tt.tuneTitle, "
                                        + "tt.composerCreditOverride, tt.arrangerCreditOverride, tt.linkUrl) "
                                        + "FROM TrackTuneTableRecord tt "
                                        + "WHERE tt.track.domainId IN (:trackIds) "
                                        + "ORDER BY tt.track.domainId, tt.id.seq",
                                AlbumTrackTuneRow.class)
                        .setParameter("trackIds", trackIds).getResultList());
    }

    /**
     * タイトルでアルバムを検索
     *
     * @param title
     *            アルバムタイトル
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByTitle(String title) {
        return list("title", title);
    }

    /**
     * アーティスト表示名でアルバムを検索
     *
     * @param artistDisplayName
     *            アーティスト表示名
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByArtistDisplayName(String artistDisplayName) {
        return list("artistDisplayName", artistDisplayName);
    }

    /**
     * イベント名でアルバムを検索
     *
     * @param eventName
     *            イベント名
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByEventName(String eventName) {
        return list("eventName", eventName);
    }

    /**
     * カタログナンバーでアルバムを検索
     *
     * @param catalogNumber
     *            カタログナンバー
     * @return 該当するアルバム（存在しない場合はnull）
     */
    public Uni<AlbumTableRecord> findByCatalogNumber(String catalogNumber) {
        return find("catalogNumber", catalogNumber).firstResult();
    }

    /**
     * リリース年でアルバムを検索
     *
     * @param year
     *            リリース年
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByReleaseYear(int year) {
        final LocalDate startDate = LocalDate.of(
                year,
                1,
                1);
        final LocalDate endDate = LocalDate.of(
                year,
                12,
                31);

        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT a FROM AlbumTableRecord a "
                                        + "WHERE a.releaseDate >= :startDate AND a.releaseDate <= :endDate",
                                AlbumTableRecord.class)
                        .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList());
    }

    /**
     * ページ指定でアルバムを検索（一覧表示用・トラックは含まない）
     *
     * <p>
     * 一覧表示の Read
     * Model（{@link com.abservice.application.query.album.model.AlbumView}）は
     * トラックを含まないフラットDTOのため、JOIN FETCHを伴わない単純なページングで問題ない （JOIN
     * FETCH併用時のページング崩れを回避できる）。件数・総ページ数は返された {@link PanacheQuery} 自身の
     * {@code count()}/{@code pageCount()} から取得する。{@code visibility}に
     * {@link Visibility#PUBLIC_ONLY}を渡すと、公開向けQuery（{@code ListAlbumsService}）専用として
     * {@code publishedAt}が非nullのアルバムのみを対象にする。
     * </p>
     *
     * <p>
     * タイトル・カタログナンバーでの絞り込みは、指定されたときだけ条件に加わる。いずれも部分一致で大文字小文字を問わず、
     * 両方を指定した場合は積（AND）で絞り込む。利用者が打った語に含まれる {@code %} / {@code _} / {@code \}
     * はワイルドカードではなく文字そのものとして扱う（{@link #likeContains}）。
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
     * @param title
     *            タイトルでの絞り込み（nullable。未指定なら絞り込まない）
     * @param catalogNumber
     *            カタログナンバーでの絞り込み（nullable。未指定なら絞り込まない）
     * @return ページングクエリ
     */
    public PanacheQuery<AlbumTableRecord> pagedQuery(
            int page,
            int size,
            Visibility visibility,
            SortSpec sort,
            @Nullable String title,
            @Nullable String catalogNumber) {
        return conditionsOf(
                visibility,
                title,
                catalogNumber).stream()
                .reduce((left, right) -> left + " AND " + right)
                .map(
                        where -> find(
                                where,
                                SortOrders.of(sort),
                                parametersOf(title, catalogNumber)))
                .orElseGet(() -> findAll(SortOrders.of(sort)))
                .page(Page.of(page, size));
    }

    private static List<String> conditionsOf(
            Visibility visibility,
            @Nullable String title,
            @Nullable String catalogNumber) {
        return Stream.of(
                conditionIf(visibility == Visibility.PUBLIC_ONLY, WHERE_PUBLISHED),
                conditionIf(title != null, WHERE_TITLE),
                conditionIf(catalogNumber != null, WHERE_CATALOG_NUMBER))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<String> conditionIf(boolean applies, String condition) {
        return applies
                ? Optional.of(condition)
                : Optional.empty();
    }

    private static Map<String, Object> parametersOf(@Nullable String title, @Nullable String catalogNumber) {
        return Stream.of(
                parameterIf("title", title),
                parameterIf("catalogNumber", catalogNumber))
                .flatMap(Optional::stream)
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue));
    }

    private static Optional<Map.Entry<String, Object>> parameterIf(String name, @Nullable String keyword) {
        return Optional.ofNullable(keyword)
                .map(present -> Map.entry(name, likeContains(present)));
    }

    /**
     * 部分一致の LIKE パターンへ変換する。
     *
     * <p>
     * 語そのものに含まれる {@code \} / {@code %} / {@code _} を {@code \} でエスケープしてから前後を
     * {@code %} で囲む。エスケープしないと、利用者が打った {@code _} が任意の1文字に化けて意図より広く当たる。
     * </p>
     *
     * @param keyword
     *            利用者が打った語
     * @return LIKE のパターン
     */
    static String likeContains(String keyword) {
        return "%"
                + LIKE_WILDCARDS
                        .matcher(keyword)
                        .replaceAll("\\\\$1")
                + "%";
    }

    /**
     * アルバムIDで削除
     *
     * @param domainId
     *            アルバムドメインID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByAlbumId(String domainId) {
        return delete("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * 複数のドメインIDでアルバムを一括削除
     *
     * @param domainIds
     *            アルバムのドメインID群
     * @return 完了シグナル
     */
    public Uni<Void> deleteByAlbumIds(Collection<String> domainIds) {
        return delete("domainId in ?1", domainIds).replaceWithVoid();
    }

    /**
     * アルバムIDでアルバムが存在するか確認
     *
     * @param domainId
     *            アルバムドメインID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByAlbumId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * 当該チューンを参照しているトラック内チューン構成があるか確認
     *
     * <p>
     * {@code track_tune.tune_id} はチューン集約へのドメインID参照でDBの外部キーを持たないため、参照の有無は
     * この問い合わせで確かめる。参照している側（アルバム集約内の{@code TrackTune}）を数えるので、アルバム側の データアクセスに置く。
     * </p>
     *
     * @param tuneDomainId
     *            チューンのドメインID
     * @return 参照している構成が1件以上あればtrue
     */
    public Uni<Boolean> existsTrackTuneReferencing(String tuneDomainId) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT count(tt) FROM TrackTuneTableRecord tt WHERE tt.tuneId = :tuneId",
                                Long.class)
                        .setParameter("tuneId", tuneDomainId).getSingleResult())
                .map(count -> count > 0);
    }
}
