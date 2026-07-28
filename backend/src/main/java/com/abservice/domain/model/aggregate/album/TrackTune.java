package com.abservice.domain.model.aggregate.album;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * トラック内チューン構成（集約内エンティティ）
 *
 * <p>
 * 1トラック内に含まれる個々のチューンと、その順番・クレジット上書き・リンクを表します。 トラック内では {@code seq} で識別され（永続化上は
 * trackId との複合キー）、{@code tuneId} は録音実績の一部として生成後は不変です。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class TrackTune implements DomainEntity<TrackTune, Integer> {
    /** トラック内での登場順（1, 2, 3, ...） */
    @EqualsAndHashCode.Include
    private final Integer seq;
    /** MC、環境音などの場合はnull */
    private final Tune.@Nullable Id tuneId;
    /** nullの場合はTune側のデフォルトを使用 */
    @Nullable
    private final Credit composerCreditOverride;
    /** nullの場合はTune側のデフォルトを使用 */
    @Nullable
    private final Credit arrangerCreditOverride;
    /** 外部リンク（the session、自サイト等） */
    @Nullable
    private final Url linkUrl;

    /** seq必須違反時のエラー */
    private static final ErrorResult SEQ_REQUIRED_ERROR = new ErrorResult(
            "seq",
            "Seq cannot be null",
            "SEQ_REQUIRED");

    private TrackTune(Integer seq, Tune.@Nullable Id tuneId, @Nullable Credit composerCreditOverride,
            @Nullable Credit arrangerCreditOverride, @Nullable Url linkUrl) {
        this.seq = seq;
        this.tuneId = tuneId;
        this.composerCreditOverride = composerCreditOverride;
        this.arrangerCreditOverride = arrangerCreditOverride;
        this.linkUrl = linkUrl;
    }

    private static TrackTune factory(@Nullable Integer seq, Tune.@Nullable Id tuneId,
            @Nullable Credit composerCreditOverride, @Nullable Credit arrangerCreditOverride,
            @Nullable Url linkUrl) {
        return Policy.<Stub>of(
                self -> self.seq() != null,
                SEQ_REQUIRED_ERROR)
                .verify(
                        new Stub(
                                seq,
                                tuneId,
                                composerCreditOverride,
                                arrangerCreditOverride,
                                linkUrl),
                        Stub::asTrackTune)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Integer seq, Tune.Id tuneId, Credit composerCreditOverride,
            Credit arrangerCreditOverride, Url linkUrl) {

        @AggregateFactory
        @NonNull
        TrackTune asTrackTune() {
            return new TrackTune(Objects.requireNonNull(seq), tuneId(), composerCreditOverride(),
                    arrangerCreditOverride(), linkUrl());
        }
    }

    /**
     * トラック内での識別子（{@code seq}）を取得する
     *
     * @return トラック内での登場順
     */
    @Override
    public Integer id() {
        return seq;
    }

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
    public static TrackTune create(Integer seq, Tune.@Nullable Id tuneId, @Nullable Credit composerCreditOverride,
            @Nullable Credit arrangerCreditOverride, @Nullable Url linkUrl) {
        return TrackTune.factory(
                seq,
                tuneId,
                composerCreditOverride,
                arrangerCreditOverride,
                linkUrl);
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
    public static TrackTune reconstruct(Integer seq, Tune.@Nullable Id tuneId, @Nullable Credit composerCreditOverride,
            @Nullable Credit arrangerCreditOverride, @Nullable Url linkUrl) {
        return TrackTune.factory(
                seq,
                tuneId,
                composerCreditOverride,
                arrangerCreditOverride,
                linkUrl);
    }

    /**
     * 作曲者クレジット上書きを変更
     *
     * @param newComposerCreditOverride
     *            新しい作曲者クレジット上書き
     * @return 更新されたTrackTune
     */
    public TrackTune changeComposerCreditOverride(@Nullable Credit newComposerCreditOverride) {
        return TrackTune.factory(
                seq,
                tuneId,
                newComposerCreditOverride,
                arrangerCreditOverride,
                linkUrl);
    }

    /**
     * アレンジャークレジット上書きを変更
     *
     * @param newArrangerCreditOverride
     *            新しいアレンジャークレジット上書き
     * @return 更新されたTrackTune
     */
    public TrackTune changeArrangerCreditOverride(@Nullable Credit newArrangerCreditOverride) {
        return TrackTune.factory(
                seq,
                tuneId,
                composerCreditOverride,
                newArrangerCreditOverride,
                linkUrl);
    }

    /**
     * リンクURLを変更
     *
     * @param newLinkUrl
     *            新しいリンクURL
     * @return 更新されたTrackTune
     */
    public TrackTune changeLinkUrl(@Nullable Url newLinkUrl) {
        return TrackTune.factory(
                seq,
                tuneId,
                composerCreditOverride,
                arrangerCreditOverride,
                newLinkUrl);
    }
}
