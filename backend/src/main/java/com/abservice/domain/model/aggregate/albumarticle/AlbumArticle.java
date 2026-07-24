package com.abservice.domain.model.aggregate.albumarticle;

import static com.abservice.lib.Optionals.optionally;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.lib.ErrorResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事集約ルート
 *
 * <p>
 * Web記事・お品書き用テキスト、頒布条件、入手経路を管理する集約です。 Album集約とは別のトランザクション境界を持ちます。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlbumArticle implements Aggregate<AlbumArticle, Album.Id> {
    @EqualsAndHashCode.Include
    private final Album.Id albumId; // Album集約への参照（IDのみ）
    @Nullable
    private final String introLong; // nullable: 記事本文としての紹介コメント
    @Nullable
    private final String introShort; // nullable: お品書き用のショートコメント
    @Nullable
    private final String firstEventSpace; // nullable: 初出イベントのスペース（例: "東X-00b"）
    @Nullable
    private final LabelTag labelTag; // nullable: お品書き用ラベル
    @Nullable
    private final AlbumDistribution distribution; // nullable: 頒布情報
    private final List<AlbumAcquisitionChannel> acquisitionChannels; // 入手経路

    /**
     * 新規AlbumArticleを生成
     *
     * @param albumId
     *            アルバムID
     * @param introLong
     *            記事本文（nullable）
     * @param introShort
     *            ショートコメント（nullable）
     * @param firstEventSpace
     *            初出イベントスペース（nullable）
     * @param labelTag
     *            ラベルタグ（nullable）
     * @param distribution
     *            頒布情報（nullable）
     * @return 新規AlbumArticle
     */
    public static AlbumArticle create(Album.Id albumId, @Nullable String introLong, @Nullable String introShort,
            @Nullable String firstEventSpace, @Nullable LabelTag labelTag, @Nullable AlbumDistribution distribution) {
        Policy.<Album.Id>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "albumId",
                        "Album ID cannot be null",
                        "ALBUM_ID_REQUIRED"))
                .verify(albumId, Function.identity())
                .resolve(Policy::illegalArgument);
        return new AlbumArticle(albumId, introLong, introShort, firstEventSpace, labelTag, distribution,
                Collections.emptyList());
    }

    /**
     * 永続化層からの再構成
     *
     * @param albumId
     *            アルバムID
     * @param introLong
     *            記事本文（nullable）
     * @param introShort
     *            ショートコメント（nullable）
     * @param firstEventSpace
     *            初出イベントスペース（nullable）
     * @param labelTag
     *            ラベルタグ（nullable）
     * @param distribution
     *            頒布情報（nullable）
     * @param acquisitionChannels
     *            入手経路リスト
     * @return 再構成されたAlbumArticle
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // 永続化からの再構成で全項目を受け取るため引数が多い
    public static AlbumArticle reconstruct(Album.Id albumId, @Nullable String introLong, @Nullable String introShort,
            @Nullable String firstEventSpace, @Nullable LabelTag labelTag, @Nullable AlbumDistribution distribution,
            List<AlbumAcquisitionChannel> acquisitionChannels) {
        return new AlbumArticle(albumId, introLong, introShort, firstEventSpace, labelTag, distribution,
                acquisitionChannels);
    }

    /**
     * 紹介文を更新
     *
     * @param newIntroLong
     *            新しい記事本文
     * @param newIntroShort
     *            新しいショートコメント
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle updateIntro(@Nullable String newIntroLong, @Nullable String newIntroShort) {
        return withIntroLong(newIntroLong)
                .withIntroShort(newIntroShort);
    }

    /**
     * 初出イベントスペースを変更
     *
     * @param newFirstEventSpace
     *            新しいイベントスペース
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle changeFirstEventSpace(@Nullable String newFirstEventSpace) {
        return withFirstEventSpace(newFirstEventSpace);
    }

    /**
     * ラベルタグを更新
     *
     * @param newLabelTag
     *            新しいラベルタグ
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle updateLabelTag(@Nullable LabelTag newLabelTag) {
        return withLabelTag(newLabelTag);
    }

    /**
     * 頒布情報を設定
     *
     * @param newDistribution
     *            新しい頒布情報
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle setDistribution(@Nullable AlbumDistribution newDistribution) {
        return withDistribution(newDistribution);
    }

    /**
     * 入手経路を追加
     *
     * @param channel
     *            追加する入手経路
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle addAcquisitionChannel(AlbumAcquisitionChannel channel) {
        Policy.<AlbumAcquisitionChannel>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "channel",
                        "Acquisition channel cannot be null",
                        "ACQUISITION_CHANNEL_REQUIRED"))
                .verify(channel, Function.identity())
                .resolve(Policy::illegalArgument);
        // IDの重複チェック（ビジネスルール違反 → 409）
        acquisitionChannels.stream().filter(channel::equivalentTo).findFirst().ifPresent(dup -> {
            throw new BusinessRuleViolationException(
                    "Acquisition channel with ID " + channel.id().value() + " already exists");
        });
        return withAcquisitionChannels(Stream.concat(acquisitionChannels.stream(), Stream.of(channel)).toList());
    }

    /**
     * 入手経路を削除
     *
     * @param channelId
     *            削除する入手経路のID
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle removeAcquisitionChannel(AlbumAcquisitionChannel.Id channelId) {
        Policy.<AlbumAcquisitionChannel.Id>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "channelId",
                        "Channel ID cannot be null",
                        "CHANNEL_ID_REQUIRED"))
                .verify(channelId, Function.identity())
                .resolve(Policy::illegalArgument);
        acquisitionChannels.stream().filter(c -> c.hasId(channelId)).findFirst().orElseThrow(
                () -> new BusinessRuleViolationException(
                        "Acquisition channel with ID " + channelId.value() + " not found"));
        return withAcquisitionChannels(acquisitionChannels.stream().filter(not(c -> c.hasId(channelId))).toList());
    }

    /**
     * 入手経路を更新
     *
     * @param updatedChannel
     *            更新する入手経路
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle updateAcquisitionChannel(AlbumAcquisitionChannel updatedChannel) {
        Policy.<AlbumAcquisitionChannel>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "updatedChannel",
                        "Updated channel cannot be null",
                        "ACQUISITION_CHANNEL_REQUIRED"))
                .verify(updatedChannel, Function.identity())
                .resolve(Policy::illegalArgument);
        acquisitionChannels.stream().filter(updatedChannel::equivalentTo).findFirst().orElseThrow(
                () -> new BusinessRuleViolationException(
                        "Acquisition channel with ID " + updatedChannel.id().value() + " not found"));
        return acquisitionChannels.stream()
                .map(
                        c -> c.equivalentTo(updatedChannel)
                                ? updatedChannel
                                : c)
                .collect(optionally(toUnmodifiableList()))
                .map(this::withAcquisitionChannels)
                .get();
    }

    /**
     * 入手経路リストを取得（不変）
     *
     * @return 入手経路リストの不変コピー
     */
    public List<AlbumAcquisitionChannel> getAcquisitionChannels() {
        return Collections.unmodifiableList(acquisitionChannels);
    }

    @Override
    public Album.Id id() {
        return albumId;
    }
}
