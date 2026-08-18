package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Collection;
import java.util.List;

/**
 * Tune DataSource (DAO)
 *
 * <p>
 * Panacheを使用したチューンデータアクセス層。
 * </p>
 */
@ApplicationScoped
public class TuneDataSource implements PanacheRepositoryBase<TuneTableRecord, Long> {

    private final Mutiny.SessionFactory sessionFactory;

    public TuneDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * ドメインIDでチューンを検索
     *
     * @param domainId
     *            チューンのドメインID
     * @return 該当するチューン（存在しない場合はnull）
     */
    public Uni<TuneTableRecord> findByDomainId(String domainId) {
        return find("domainId", domainId).firstResult();
    }

    /**
     * 複数のドメインIDでチューンを一括検索
     *
     * @param domainIds
     *            チューンのドメインID群
     * @return 該当するチューンのリスト
     */
    public Uni<List<TuneTableRecord>> findByIds(Collection<String> domainIds) {
        return list("domainId in ?1", domainIds);
    }

    /**
     * タイトルでチューンを検索
     *
     * @param title
     *            チューンタイトル
     * @return 該当するチューンのリスト
     */
    public Uni<List<TuneTableRecord>> findByTitle(String title) {
        return list("title", title);
    }

    /**
     * チューン種別でチューンを検索
     *
     * @param tuneKind
     *            チューン種別
     * @return 該当するチューンのリスト
     */
    public Uni<List<TuneTableRecord>> findByTuneKind(String tuneKind) {
        return list("tuneKind", tuneKind);
    }

    /**
     * チューンタイプでチューンを検索
     *
     * @param tuneType
     *            チューンタイプ
     * @return 該当するチューンのリスト
     */
    public Uni<List<TuneTableRecord>> findByTuneType(String tuneType) {
        return list("tuneType", tuneType);
    }

    /**
     * デフォルトキーでチューンを検索
     *
     * @param defaultKey
     *            デフォルトキー
     * @return 該当するチューンのリスト
     */
    public Uni<List<TuneTableRecord>> findByDefaultKey(String defaultKey) {
        return list("defaultKey", defaultKey);
    }

    /**
     * ページ指定でチューンを検索（一覧表示用）
     *
     * <p>
     * 件数・総ページ数は返された {@link PanacheQuery} 自身の {@code count()}/{@code pageCount()}
     * から取得する。
     * </p>
     *
     * @param page
     *            ページ番号（0始まり）
     * @param size
     *            1ページの件数
     * @return ページングクエリ
     */
    public PanacheQuery<TuneTableRecord> pagedQuery(int page, int size) {
        return findAll(Sort.by("tuneId"))
                .page(Page.of(page, size));
    }

    /**
     * チューンIDで削除
     *
     * @param id
     *            チューンID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByTuneId(String domainId) {
        return delete("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * 複数のドメインIDでチューンを一括削除
     *
     * @param domainIds
     *            チューンのドメインID群
     * @return 完了シグナル
     */
    public Uni<Void> deleteByTuneIds(Collection<String> domainIds) {
        return delete("domainId in ?1", domainIds).replaceWithVoid();
    }

    /**
     * チューンIDでチューンが存在するか確認
     *
     * @param id
     *            チューンID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByTuneId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }
}
