package com.abservice.domain.model.aggregate.albumarticle;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import java.util.Objects;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * アルバム入手経路（集約内エンティティ）
 *
 * <p>
 * 入手経路（委託ショップ、BOOTH、Bandcamp、自サイト通販など）を管理します。
 * </p>
 */
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

    private static final ErrorResult CHANNEL_TYPE_REQUIRED_ERROR = new ErrorResult(
            "channelType",
            "Channel type cannot be null",
            "CHANNEL_TYPE_REQUIRED");

    private static final ErrorResult NAME_REQUIRED_ERROR = new ErrorResult(
            "name",
            "Name cannot be blank",
            "NAME_REQUIRED");

    // 全フィールドを受け取る唯一の構築経路。自身では検証しないため、factory以外から呼ばせない
    // （ArchUnitで強制、#101）。
    private AlbumAcquisitionChannel(Id id, ChannelType channelType, String name, @Nullable Url url,
            @Nullable String note) {
        this.id = id;
        this.channelType = channelType;
        this.name = name;
        this.url = url;
        this.note = note;
    }

    // 生の全項目を受け取り、Policy検証を経てAlbumAcquisitionChannelを生成する唯一のfactory（#101）。
    private static AlbumAcquisitionChannel factory(@Nullable Id id, @Nullable ChannelType channelType,
            @Nullable String name, @Nullable Url url, @Nullable String note) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.channelType() != null,
                        CHANNEL_TYPE_REQUIRED_ERROR),
                Policy.of(
                        self -> StringUtils.isNotBlank(self.name()),
                        NAME_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                channelType,
                                name,
                                url,
                                note),
                        Stub::asAlbumAcquisitionChannel)
                .resolve(Policy::illegalArgument);
    }

    // AlbumAcquisitionChannelのAllArgsConstructorと同形の、制約を持たないdumbな入れ物。全フィールドが
    // 自明にnullableなので@NullUnmarkedでNullAwareの対象外にし、個別の@Nullable注釈を省く。
    // ArchUnit（stubShouldMatchEnclosingConstructor）が実コンストラクタとの引数一致を機械的に強制する。
    @NullUnmarked
    private record Stub(Id id, ChannelType channelType, String name, Url url, String note) {

        @AggregateFactory
        @NonNull
        AlbumAcquisitionChannel asAlbumAcquisitionChannel() {
            return new AlbumAcquisitionChannel(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(channelType),
                    Objects.requireNonNull(name),
                    url(),
                    note());
        }
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
        return AlbumAcquisitionChannel.factory(
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
        return AlbumAcquisitionChannel.factory(
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
        return AlbumAcquisitionChannel.factory(
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
        return AlbumAcquisitionChannel.factory(
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
        return AlbumAcquisitionChannel.factory(
                id,
                channelType,
                name,
                newUrl,
                note);
    }

    /**
     * 補足を変更
     *
     * @param newNote
     *            新しい補足
     * @return 更新されたAlbumAcquisitionChannel
     */
    public AlbumAcquisitionChannel changeNote(String newNote) {
        return AlbumAcquisitionChannel.factory(
                id,
                channelType,
                name,
                url,
                newNote);
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
