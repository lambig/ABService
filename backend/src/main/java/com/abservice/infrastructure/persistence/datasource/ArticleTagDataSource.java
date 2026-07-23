package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.ArticleTagEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
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
public class ArticleTagDataSource implements PanacheRepositoryBase<ArticleTagEntity, Long> {

    /**
     * ドメインIDの集合に一致する記事タグを取得する
     *
     * @param domainIds
     *            ドメインIDの集合
     * @return 該当する記事タグのリスト
     */
    public Uni<List<ArticleTagEntity>> findByDomainIds(Collection<String> domainIds) {
        return domainIds.isEmpty()
                ? Uni.createFrom().item(List.of())
                : list("domainId in ?1", domainIds);
    }

    /**
     * 新規の記事タグを1件ずつ順に永続化する（IDENTITY採番を確実に解決するため逐次実行）
     *
     * @param entities
     *            永続化する記事タグエンティティのリスト
     * @return 完了シグナル
     */
    public Uni<Void> persistAll(List<ArticleTagEntity> entities) {
        return Multi.createFrom().iterable(entities)
                .onItem().transformToUniAndConcatenate(this::persist)
                .collect().asList()
                .replaceWithVoid();
    }
}
