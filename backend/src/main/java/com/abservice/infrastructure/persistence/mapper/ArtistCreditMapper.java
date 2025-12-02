package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.artistcredit.ArtistCredit;
import com.abservice.domain.model.vo.common.ArtistCreditName;
import com.abservice.infrastructure.persistence.entity.ArtistCreditEntity;

/**
 * ArtistCredit Mapper
 *
 * <p>
 * ArtistCreditドメインモデルとArtistCreditEntityの相互変換を担当します。
 * </p>
 */
public class ArtistCreditMapper {

    private ArtistCreditMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            ArtistCreditEntity
     * @return ArtistCredit
     */
    public static ArtistCredit toDomain(ArtistCreditEntity entity) {
        if (entity == null) {
            return null;
        }

        return new ArtistCredit(new ArtistCredit.Id(entity.getDomainId()),
                new ArtistCreditName(entity.getDisplayName()), entity.getSortKey());
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param artistCredit
     *            ArtistCredit
     * @return ArtistCreditEntity
     */
    public static ArtistCreditEntity toEntity(ArtistCredit artistCredit) {
        if (artistCredit == null) {
            return null;
        }

        var artistCreditEntity = new ArtistCreditEntity();
        artistCreditEntity.setDomainId(artistCredit.id().value());
        artistCreditEntity.setDisplayName(artistCredit.displayName().value());
        artistCreditEntity.setSortKey(artistCredit.sortKey());

        return artistCreditEntity;
    }
}
