package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
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
