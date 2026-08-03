package com.abservice.application.query.album;

import com.abservice.application.query.album.model.AlbumView;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;

/**
 * アルバムエンティティから Read Model（{@link AlbumView}）への変換
 *
 * <p>
 * CQRS の Read 側マッパー。{@code infrastructure.persistence.datasource} が返す
 * {@link AlbumTableRecord} を照会結果 DTO へ平坦化します。ドメインモデルを経由しません。
 * </p>
 */
final class AlbumViewMapper {

    private AlbumViewMapper() {
    }

    /**
     * エンティティを Read Model へ変換します。
     *
     * @param entity
     *            アルバムエンティティ
     * @return アルバムの Read Model
     */
    static AlbumView toView(AlbumTableRecord entity) {
        return new AlbumView(
                entity.getDomainId(),
                entity.getTitle(),
                entity.getReleaseDate().toString(),
                entity.getArtistDisplayName(),
                entity.getArtistSortKey(),
                entity.getCatalogNumber(),
                entity.getIsdn());
    }
}
