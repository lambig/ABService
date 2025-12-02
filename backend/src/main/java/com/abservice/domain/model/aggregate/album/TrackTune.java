package com.abservice.domain.model.aggregate.album;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

/**
 * トラック内チューン構成（集約内エンティティ）
 *
 * <p>
 * 1トラック内に含まれる個々のチューンと、その順番・クレジット上書き・リンクを表します。 複合キー (trackId, seq) で識別されます。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TrackTune {
    @EqualsAndHashCode.Include
    private final Integer seq; // トラック内での登場順（1, 2, 3, ...）
    private final Tune.Id tuneId; // nullable: MC、環境音などの場合はnull
    private final Credit composerCreditOverride; // nullable: nullの場合はTune側のデフォルトを使用
    private final Credit arrangerCreditOverride; // nullable: nullの場合はTune側のデフォルトを使用
    private final Url linkUrl; // nullable: 外部リンク（the session, 自サイト等）

    /**
     * 新規TrackTuneを生成
     *
     * @param seq
     *            シーケンス番号
     * @param tuneId
     *            チューンID（nullable）
     * @param composerCreditOverride
     *            作曲者クレジット上書き（nullable）
     * @param arrangerCreditOverride
     *            アレンジャークレジット上書き（nullable）
     * @param linkUrl
     *            リンクURL（nullable）
     * @return 新規TrackTune
     */
    public static TrackTune create(Integer seq, Tune.Id tuneId, Credit composerCreditOverride,
            Credit arrangerCreditOverride, Url linkUrl) {
        if (seq == null) {
            throw new IllegalArgumentException("Seq cannot be null");
        }
        return new TrackTune(seq, tuneId, composerCreditOverride, arrangerCreditOverride, linkUrl);
    }

    /**
     * 永続化層からの再構成
     *
     * @param seq
     *            シーケンス番号
     * @param tuneId
     *            チューンID（nullable）
     * @param composerCreditOverride
     *            作曲者クレジット上書き（nullable）
     * @param arrangerCreditOverride
     *            アレンジャークレジット上書き（nullable）
     * @param linkUrl
     *            リンクURL（nullable）
     * @return 再構成されたTrackTune
     */
    public static TrackTune reconstruct(Integer seq, Tune.Id tuneId, Credit composerCreditOverride,
            Credit arrangerCreditOverride, Url linkUrl) {
        return new TrackTune(seq, tuneId, composerCreditOverride, arrangerCreditOverride, linkUrl);
    }

    /**
     * チューンIDを変更
     *
     * @param newTuneId
     *            新しいチューンID
     * @return 更新されたTrackTune
     */
    public TrackTune changeTuneId(Tune.Id newTuneId) {
        return withTuneId(newTuneId);
    }

    /**
     * 作曲者クレジット上書きを変更
     *
     * @param newComposerCreditOverride
     *            新しい作曲者クレジット上書き
     * @return 更新されたTrackTune
     */
    public TrackTune changeComposerCreditOverride(Credit newComposerCreditOverride) {
        return withComposerCreditOverride(newComposerCreditOverride);
    }

    /**
     * アレンジャークレジット上書きを変更
     *
     * @param newArrangerCreditOverride
     *            新しいアレンジャークレジット上書き
     * @return 更新されたTrackTune
     */
    public TrackTune changeArrangerCreditOverride(Credit newArrangerCreditOverride) {
        return withArrangerCreditOverride(newArrangerCreditOverride);
    }

    /**
     * リンクURLを変更
     *
     * @param newLinkUrl
     *            新しいリンクURL
     * @return 更新されたTrackTune
     */
    public TrackTune changeLinkUrl(Url newLinkUrl) {
        return withLinkUrl(newLinkUrl);
    }
}
