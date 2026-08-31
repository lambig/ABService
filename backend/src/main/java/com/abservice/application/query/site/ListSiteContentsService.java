package com.abservice.application.query.site;

import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.SiteContentDataSource;
import com.abservice.infrastructure.persistence.entity.SiteContentTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.AllArgsConstructor;

/**
 * サイト文言の全件照会サービス
 *
 * <p>
 * CQRS の Read 側ユースケース。ドメイン・Repository を経由せず {@link SiteContentDataSource} から直接
 * Read Model を組み立てます。
 * </p>
 *
 * <p>
 * 公開向けと管理向けで同じ結果を返します。文言は「どちらから見ても同じもの」であり、要求元で項目が変わりません。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ListSiteContentsService implements QueryService<ListSiteContentsQuery, ListSiteContentsResult> {

    private final SiteContentDataSource dataSource;

    @WithSession
    @Override
    public Uni<ListSiteContentsResult> query(ListSiteContentsQuery query) {
        return dataSource.listAllOrderByKey()
                .map(ListSiteContentsService::toResult);
    }

    static ListSiteContentsResult toResult(List<SiteContentTableRecord> entities) {
        return new ListSiteContentsResult(
                entities.stream()
                        .map(SiteContentViewMapper::toView)
                        .toList());
    }
}
