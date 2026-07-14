package com.abservice.domain.model.aggregate.albumarticle;

import static java.util.function.Predicate.not;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.LabelTag;
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
        Optional.ofNullable(albumId).orElseThrow(() -> new IllegalArgumentException("Album ID cannot be null"));
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
        return withIntroLong(newIntroLong).withIntroShort(newIntroShort);
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
        Optional.ofNullable(channel)
                .orElseThrow(() -> new IllegalArgumentException("Acquisition channel cannot be null"));
        // IDの重複チェック
        acquisitionChannels.stream().filter(channel::equivalentTo).findFirst().ifPresent(dup -> {
            throw new IllegalArgumentException(
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
        Optional.ofNullable(channelId).orElseThrow(() -> new IllegalArgumentException("Channel ID cannot be null"));
        acquisitionChannels.stream().filter(c -> c.hasId(channelId)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Acquisition channel with ID " + channelId.value() + " not found"));
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
        Optional.ofNullable(updatedChannel)
                .orElseThrow(() -> new IllegalArgumentException("Updated channel cannot be null"));
        acquisitionChannels.stream().filter(updatedChannel::equivalentTo).findFirst().orElseThrow(
                () -> new IllegalArgumentException(
                        "Acquisition channel with ID " + updatedChannel.id().value() + " not found"));
        return withAcquisitionChannels(
                acquisitionChannels.stream().map(
                        c -> c.equivalentTo(updatedChannel)
                                ? updatedChannel
                                : c)
                        .toList());
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
