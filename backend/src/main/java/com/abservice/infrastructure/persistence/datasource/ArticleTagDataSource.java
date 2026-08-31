package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.ArticleTagTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

/**
 * ArticleTag DataSource (DAO)
 *
 * <p>
 * Panacheを使用した記事タグ（共有語彙）データアクセス層。
 * </p>
 */
@ApplicationScoped
public class ArticleTagDataSource implements PanacheRepositoryBase<ArticleTagTableRecord, Long> {

    /**
     * ドメインIDの集合に一致する記事タグを取得する
     *
     * @param domainIds
     *            ドメインIDの集合
     * @return 該当する記事タグのリスト
     */
    public Uni<List<ArticleTagTableRecord>> findByDomainIds(Collection<String> domainIds) {
        return domainIds.isEmpty()
                ? Uni.createFrom().item(List.of())
                : list("domainId in ?1", domainIds);
    }

    /**
     * 名前で記事タグを取得する
     *
     * <p>
     * {@code name} は一意（{@code V11}）のため、該当は高々1件になる。
     * </p>
     *
     * @param name
     *            タグ名
     * @return 該当する記事タグ。存在しない場合は null
     */
    public Uni<ArticleTagTableRecord> findByName(String name) {
        return find("name", name).firstResult();
    }

    /**
     * 記事タグを名前の昇順ですべて取得する
     *
     * @return 記事タグのリスト
     */
    public Uni<List<ArticleTagTableRecord>> findAllOrderByName() {
        return listAll(Sort.by("name", Sort.Direction.Ascending));
    }

    /**
     * 新規の記事タグを1件ずつ順に永続化する（IDENTITY採番を確実に解決するため逐次実行）
     *
     * @param entities
     *            永続化する記事タグエンティティのリスト
     * @return 完了シグナル
     */
    public Uni<Void> persistAll(List<ArticleTagTableRecord> entities) {
        return Multi.createFrom().iterable(entities)
                .onItem().transformToUniAndConcatenate(this::persist)
                .collect().asList()
                .replaceWithVoid();
    }
}
