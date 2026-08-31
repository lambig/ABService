package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.site.SiteContent;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.common.MarkupFormat;
import com.abservice.domain.model.vo.site.SiteContentKey;
import com.abservice.infrastructure.persistence.entity.SiteContentTableRecord;

/**
 * SiteContent Mapper
 *
 * <p>
 * SiteContentドメインモデルとSiteContentTableRecordの相互変換を担当します。
 * </p>
 */
public final class SiteContentMapper {

    private SiteContentMapper() {
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            SiteContentTableRecord
     * @return SiteContent
     */
    public static SiteContent toDomain(SiteContentTableRecord entity) {
        return SiteContent.reconstruct(
                SiteContent.Id.of(entity.getDomainId()),
                SiteContentKey.of(entity.getContentKey()),
                new MarkupContent(
                        entity.getContent(),
                        MarkupFormat.orDefault(entity.getContentFormat())));
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param siteContent
     *            SiteContent
     * @return SiteContentTableRecord
     */
    public static SiteContentTableRecord toEntity(SiteContent siteContent) {
        return new SiteContentTableRecord()
                .setDomainId(siteContent.id().value())
                .setContentKey(siteContent.key().value())
                .setContent(siteContent.content().content())
                .setContentFormat(siteContent.content().format().name());
    }
}
