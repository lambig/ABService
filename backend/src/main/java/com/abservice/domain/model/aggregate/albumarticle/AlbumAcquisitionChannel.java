package com.abservice.domain.model.aggregate.albumarticle;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.common.Url;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;

/**
 * アルバム入手経路（集約内エンティティ）
 *
 * <p>
 * 入手経路（委託ショップ、BOOTH、Bandcamp、自サイト通販など）を管理します。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlbumAcquisitionChannel implements DomainEntity<AlbumAcquisitionChannel, AlbumAcquisitionChannel.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final ChannelType channelType;
    private final String name; // 表示用の名前
    private final Url url; // nullable: 詳細ページへのURL
    private final String note; // nullable: 補足

    /**
     * チャネルタイプを変更
     *
     * @param newChannelType
     *            新しいチャネルタイプ
     * @return 更新されたAlbumAcquisitionChannel
     */
    public AlbumAcquisitionChannel changeChannelType(ChannelType newChannelType) {
        if (newChannelType == null) {
            throw new IllegalArgumentException("Channel type cannot be null");
        }
        return withChannelType(newChannelType);
    }

    /**
     * 名前を変更
     *
     * @param newName
     *            新しい名前
     * @return 更新されたAlbumAcquisitionChannel
     */
    public AlbumAcquisitionChannel changeName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        return withName(newName);
    }

    /**
     * URLを変更
     *
     * @param newUrl
     *            新しいURL
     * @return 更新されたAlbumAcquisitionChannel
     */
    public AlbumAcquisitionChannel changeUrl(Url newUrl) {
        return withUrl(newUrl);
    }

    /**
     * 補足を変更
     *
     * @param newNote
     *            新しい補足
     * @return 更新されたAlbumAcquisitionChannel
     */
    public AlbumAcquisitionChannel changeNote(String newNote) {
        return withNote(newNote);
    }

    @Override
    public Id id() {
        return id;
    }

    /**
     * AlbumAcquisitionChannel ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(String value) implements EntityId<AlbumAcquisitionChannel> {
        public Id {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("AlbumAcquisitionChannel ID cannot be blank");
            }
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("AlbumAcquisitionChannel ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してAlbumAcquisitionChannel.Idを作成
         */
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からAlbumAcquisitionChannel.Idを生成
         */
        public static Id of(String value) {
            return new Id(value);
        }
    }
}
