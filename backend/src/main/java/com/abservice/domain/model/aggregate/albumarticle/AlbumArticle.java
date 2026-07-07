package com.abservice.domain.model.aggregate.albumarticle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.LabelTag;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

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
    private final String introLong; // nullable: 記事本文としての紹介コメント
    private final String introShort; // nullable: お品書き用のショートコメント
    private final String firstEventSpace; // nullable: 初出イベントのスペース（例: "東X-00b"）
    private final LabelTag labelTag; // nullable: お品書き用ラベル
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
    public static AlbumArticle create(Album.Id albumId, String introLong, String introShort, String firstEventSpace,
            LabelTag labelTag, AlbumDistribution distribution) {
        if (albumId == null) {
            throw new IllegalArgumentException("Album ID cannot be null");
        }
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
    public static AlbumArticle reconstruct(Album.Id albumId, String introLong, String introShort,
            String firstEventSpace, LabelTag labelTag, AlbumDistribution distribution,
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
    public AlbumArticle updateIntro(String newIntroLong, String newIntroShort) {
        return withIntroLong(newIntroLong).withIntroShort(newIntroShort);
    }

    /**
     * 初出イベントスペースを変更
     *
     * @param newFirstEventSpace
     *            新しいイベントスペース
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle changeFirstEventSpace(String newFirstEventSpace) {
        return withFirstEventSpace(newFirstEventSpace);
    }

    /**
     * ラベルタグを更新
     *
     * @param newLabelTag
     *            新しいラベルタグ
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle updateLabelTag(LabelTag newLabelTag) {
        return withLabelTag(newLabelTag);
    }

    /**
     * 頒布情報を設定
     *
     * @param newDistribution
     *            新しい頒布情報
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle setDistribution(AlbumDistribution newDistribution) {
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
        if (channel == null) {
            throw new IllegalArgumentException("Acquisition channel cannot be null");
        }
        // IDの重複チェック
        if (acquisitionChannels.stream().anyMatch(c -> c.id().equals(channel.id()))) {
            throw new IllegalArgumentException(
                    "Acquisition channel with ID " + channel.id().value() + " already exists");
        }
        var newChannels = new ArrayList<>(acquisitionChannels);
        newChannels.add(channel);
        return withAcquisitionChannels(Collections.unmodifiableList(newChannels));
    }

    /**
     * 入手経路を削除
     *
     * @param channelId
     *            削除する入手経路のID
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle removeAcquisitionChannel(AlbumAcquisitionChannel.Id channelId) {
        if (channelId == null) {
            throw new IllegalArgumentException("Channel ID cannot be null");
        }
        var newChannels = new ArrayList<>(acquisitionChannels);
        var removed = newChannels.removeIf(c -> c.id().equals(channelId));
        if (!removed) {
            throw new IllegalArgumentException("Acquisition channel with ID " + channelId.value() + " not found");
        }
        return withAcquisitionChannels(Collections.unmodifiableList(newChannels));
    }

    /**
     * 入手経路を更新
     *
     * @param updatedChannel
     *            更新する入手経路
     * @return 更新されたAlbumArticle
     */
    public AlbumArticle updateAcquisitionChannel(AlbumAcquisitionChannel updatedChannel) {
        if (updatedChannel == null) {
            throw new IllegalArgumentException("Updated channel cannot be null");
        }
        var newChannels = new ArrayList<>(acquisitionChannels);
        var index = newChannels.stream().filter(c -> c.id().equals(updatedChannel.id())).findFirst()
                .map(newChannels::indexOf).orElseThrow(() -> new IllegalArgumentException(
                        "Acquisition channel with ID " + updatedChannel.id().value() + " not found"));
        newChannels.set(index, updatedChannel);
        return withAcquisitionChannels(Collections.unmodifiableList(newChannels));
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
