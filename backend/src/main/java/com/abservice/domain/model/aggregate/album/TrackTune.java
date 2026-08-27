package com.abservice.domain.model.aggregate.album;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.TrackTuneTitle;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * トラック内チューン構成（集約内エンティティ）
 *
 * <p>
 * 1トラック内に含まれる個々のチューンと、その順番・名前・クレジット上書き・リンクを表します。 トラック内では {@code seq}
 * で識別され（永続化上は trackId との複合キー）、{@code tuneId} はトラックが表す構成の事実の一部として生成後は不変です。
 * </p>
 *
 * <p>
 * {@code tuneTitle} は<b>人が書いた記述</b>としてのチューン名です。{@code Tune} マスタとの同定を行わない現時点では、
 * これが唯一のチューンの手がかりであり、{@code tuneId} は {@code null} のままになります。
 * {@code composerCreditOverride} / {@code arrangerCreditOverride} は同定後に
 * {@code Tune} 側の 既定値を上書きする位置づけの項目で、同定前はそれ自体がこのチューンのクレジットとして扱われます。
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
    /** チューン名（人が書いた記述）。MC・環境音など名前を持たない構成要素の場合はnull */
    @Nullable
    private final TrackTuneTitle tuneTitle;
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

    /** seqが正の整数でない場合のエラー */
    private static final ErrorResult SEQ_NOT_POSITIVE_ERROR = new ErrorResult(
            "seq",
            "Seq must be a positive integer",
            "SEQ_NOT_POSITIVE");

    private TrackTune(Integer seq, Tune.@Nullable Id tuneId, @Nullable TrackTuneTitle tuneTitle,
            @Nullable Credit composerCreditOverride, @Nullable Credit arrangerCreditOverride,
            @Nullable Url linkUrl) {
        this.seq = seq;
        this.tuneId = tuneId;
        this.tuneTitle = tuneTitle;
        this.composerCreditOverride = composerCreditOverride;
        this.arrangerCreditOverride = arrangerCreditOverride;
        this.linkUrl = linkUrl;
    }

    private static TrackTune factory(@Nullable Integer seq, Tune.@Nullable Id tuneId,
            @Nullable TrackTuneTitle tuneTitle, @Nullable Credit composerCreditOverride,
            @Nullable Credit arrangerCreditOverride, @Nullable Url linkUrl) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.seq() != null,
                        SEQ_REQUIRED_ERROR),
                Policy.of(
                        self -> isPositiveOrAbsent(self.seq()),
                        SEQ_NOT_POSITIVE_ERROR))
                .verify(
                        new Stub(
                                seq,
                                tuneId,
                                tuneTitle,
                                composerCreditOverride,
                                arrangerCreditOverride,
                                linkUrl),
                        Stub::asTrackTune)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Integer seq, Tune.Id tuneId, TrackTuneTitle tuneTitle, Credit composerCreditOverride,
            Credit arrangerCreditOverride, Url linkUrl) {

        @AggregateFactory
        @NonNull
        TrackTune asTrackTune() {
            return new TrackTune(Objects.requireNonNull(seq), tuneId(), tuneTitle(), composerCreditOverride(),
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
     * @param tuneTitle
     *            チューン名（nullable）
     * @param composerCreditOverride
     *            作曲者クレジット上書き（nullable）
     * @param arrangerCreditOverride
     *            アレンジャークレジット上書き（nullable）
     * @param linkUrl
     *            リンクURL（nullable）
     * @return 新規TrackTune
     */
    public static TrackTune create(Integer seq, Tune.@Nullable Id tuneId, @Nullable TrackTuneTitle tuneTitle,
            @Nullable Credit composerCreditOverride, @Nullable Credit arrangerCreditOverride,
            @Nullable Url linkUrl) {
        return TrackTune.factory(
                seq,
                tuneId,
                tuneTitle,
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
     * @param tuneTitle
     *            チューン名（nullable）
     * @param composerCreditOverride
     *            作曲者クレジット上書き（nullable）
     * @param arrangerCreditOverride
     *            アレンジャークレジット上書き（nullable）
     * @param linkUrl
     *            リンクURL（nullable）
     * @return 再構成されたTrackTune
     */
    public static TrackTune reconstruct(Integer seq, Tune.@Nullable Id tuneId, @Nullable TrackTuneTitle tuneTitle,
            @Nullable Credit composerCreditOverride, @Nullable Credit arrangerCreditOverride,
            @Nullable Url linkUrl) {
        return TrackTune.factory(
                seq,
                tuneId,
                tuneTitle,
                composerCreditOverride,
                arrangerCreditOverride,
                linkUrl);
    }

    /**
     * 外部入力からチューン構成を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。{@code seq} の必須検証と、各値オブジェクトの
     * 検証を独立に行い、エラーを集約します。空白のみの文字列は未指定として扱います。
     * </p>
     *
     * <p>
     * {@code tuneId} は受け取りません。{@code Tune} マスタとの同定を行わないため、外部入力から作られる チューン構成は常に
     * {@code tuneId} を持ちません。
     * </p>
     *
     * @param seq
     *            シーケンス番号
     * @param tuneTitle
     *            チューン名（nullable）
     * @param composerCreditOverride
     *            作曲者クレジット（nullable）
     * @param arrangerCreditOverride
     *            アレンジャークレジット（nullable）
     * @param linkUrl
     *            リンクURL（nullable）
     * @return 成功時は {@code TrackTune}、失敗時はエラー
     */
    public static Result<TrackTune> fromInput(@Nullable Integer seq, @Nullable String tuneTitle,
            @Nullable String composerCreditOverride, @Nullable String arrangerCreditOverride,
            @Nullable String linkUrl) {
        return Result.zip(
                seqPolicy().verify(seq, Function.identity()),
                Result.zip(
                        optional(TrackTuneTitle::fromInput, tuneTitle),
                        optional(Credit::fromInput, composerCreditOverride),
                        optional(Credit::fromInput, arrangerCreditOverride),
                        OptionalFields::new),
                optional(Url::fromInput, linkUrl),
                (validSeq, fields, url) -> TrackTune.create(
                        validSeq,
                        null,
                        fields.tuneTitle().orElse(null),
                        fields.composerCreditOverride().orElse(null),
                        fields.arrangerCreditOverride().orElse(null),
                        url.orElse(null)));
    }

    private record OptionalFields(Optional<TrackTuneTitle> tuneTitle, Optional<Credit> composerCreditOverride,
            Optional<Credit> arrangerCreditOverride) {
    }

    private static Policy<Integer> seqPolicy() {
        return Policy.all(
                Policy.of(
                        Objects::nonNull,
                        SEQ_REQUIRED_ERROR),
                Policy.of(
                        TrackTune::isPositiveOrAbsent,
                        SEQ_NOT_POSITIVE_ERROR));
    }

    /**
     * 未指定（null）か正の整数かを判定する。
     *
     * <p>
     * 必須検証と値域検証を独立に評価してエラーを集約するため、未指定は本判定では違反にしない。
     * </p>
     *
     * @param seq
     *            シーケンス番号（nullable）
     * @return 未指定または正の整数ならtrue
     */
    private static boolean isPositiveOrAbsent(@Nullable Integer seq) {
        return Optional.ofNullable(seq)
                .map(value -> value > 0)
                .orElse(true);
    }

    private static <T> Result<Optional<T>> optional(Function<String, Result<T>> fromInput, @Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        present -> fromInput.apply(present)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<T>>success(Optional.empty()));
    }

    /**
     * チューン名を変更
     *
     * @param newTuneTitle
     *            新しいチューン名
     * @return 更新されたTrackTune
     */
    public TrackTune changeTuneTitle(@Nullable TrackTuneTitle newTuneTitle) {
        return TrackTune.factory(
                seq,
                tuneId,
                newTuneTitle,
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
                tuneTitle,
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
                tuneTitle,
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
                tuneTitle,
                composerCreditOverride,
                arrangerCreditOverride,
                newLinkUrl);
    }
}
