package com.abservice.domain.model.aggregate.albumarticle;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバム入手経路（集約内エンティティ）
 *
 * <p>
 * 入手経路（委託ショップ、BOOTH、Bandcamp、自サイト通販など）を管理します。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class AlbumAcquisitionChannel
        implements
            DomainEntity<AlbumAcquisitionChannel, AlbumAcquisitionChannel.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final ChannelType channelType;
    private final String name; // 表示用の名前
    @Nullable
    private final Url url; // nullable: 詳細ページへのURL
    @Nullable
    private final String note; // nullable: 補足

    // 全フィールドを受け取る唯一の構築経路（@Withが生成するwitherも本コンストラクタを呼ぶ）。
    // Policy検証をここに一本化することで、witherを含むどの経路からも検証を迂回できない（#101）。
    private AlbumAcquisitionChannel(Id id, ChannelType channelType, String name, @Nullable Url url,
            @Nullable String note) {
        this.id = id;
        this.channelType = requireChannelType(channelType);
        this.name = requireName(name);
        this.url = url;
        this.note = note;
    }

    private static ChannelType requireChannelType(@Nullable ChannelType channelType) {
        return Policy.<ChannelType>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "channelType",
                        "Channel type cannot be null",
                        "CHANNEL_TYPE_REQUIRED"))
                .verify(channelType, Function.identity())
                .resolve(Policy::illegalArgument);
    }

    private static String requireName(@Nullable String name) {
        return Policy.<String>of(
                StringUtils::isNotBlank,
                () -> new ErrorResult(
                        "name",
                        "Name cannot be blank",
                        "NAME_REQUIRED"))
                .verify(name, Function.identity())
                .resolve(Policy::illegalArgument);
    }

    /**
     * 新規AlbumAcquisitionChannelを生成
     *
     * @param channelType
     *            チャネルタイプ
     * @param name
     *            名前
     * @param url
     *            URL（nullable）
     * @param note
     *            補足（nullable）
     * @return 新規AlbumAcquisitionChannel
     */
    public static AlbumAcquisitionChannel create(
            ChannelType channelType,
            String name,
            @Nullable Url url,
            @Nullable String note) {
        return new AlbumAcquisitionChannel(
                Id.generate(),
                channelType,
                name,
                url,
                note);
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            ID
     * @param channelType
     *            チャネルタイプ
     * @param name
     *            名前
     * @param url
     *            URL（nullable）
     * @param note
     *            補足（nullable）
     * @return 再構成されたAlbumAcquisitionChannel
     */
    public static AlbumAcquisitionChannel reconstruct(Id id, ChannelType channelType, String name,
            @Nullable Url url, @Nullable String note) {
        return new AlbumAcquisitionChannel(
                id,
                channelType,
                name,
                url,
                note);
    }

    /**
     * チャネルタイプを変更
     *
     * @param newChannelType
     *            新しいチャネルタイプ
     * @return 更新されたAlbumAcquisitionChannel
     */
    public AlbumAcquisitionChannel changeChannelType(ChannelType newChannelType) {
        return new AlbumAcquisitionChannel(
                id,
                newChannelType,
                name,
                url,
                note);
    }

    /**
     * 名前を変更
     *
     * @param newName
     *            新しい名前
     * @return 更新されたAlbumAcquisitionChannel
     */
    public AlbumAcquisitionChannel changeName(String newName) {
        return new AlbumAcquisitionChannel(
                id,
                channelType,
                newName,
                url,
                note);
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
            Policy.<String>all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "AlbumAcquisitionChannel ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult("value", "AlbumAcquisitionChannel ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")))
                    .verify(value, Function.identity())
                    .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
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
