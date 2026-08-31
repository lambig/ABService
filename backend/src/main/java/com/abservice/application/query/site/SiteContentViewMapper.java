package com.abservice.application.query.site;

import com.abservice.application.query.site.model.SiteContentView;
import com.abservice.infrastructure.persistence.entity.SiteContentTableRecord;

/**
 * サイト文言の Read Model への変換
 *
 * <p>
 * CQRS の Read 側はドメイン・Repository を経由せず、{@code SiteContentTableRecord} から直接 Read
 * Model を組み立てます。
 * </p>
 */
public final class SiteContentViewMapper {

    private SiteContentViewMapper() {
    }

    /**
     * EntityからViewへ変換
     *
     * @param entity
     *            SiteContentTableRecord
     * @return SiteContentView
     */
    public static SiteContentView toView(SiteContentTableRecord entity) {
        return new SiteContentView(
                entity.getContentKey(),
                entity.getContent(),
                entity.getContentFormat());
    }
}
