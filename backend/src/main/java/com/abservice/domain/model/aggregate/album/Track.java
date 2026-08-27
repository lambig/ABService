package com.abservice.domain.model.aggregate.album;

import static com.abservice.lib.Optionals.optionally;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullUnmarked;

/**
 * トラック（集約内エンティティ）
 *
 * <p>
 * アルバムを構成する1トラックを表します。同じチューン構成であっても、改訂（revise）したものは別Trackとして扱います。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Track implements DomainEntity<Track, Track.Id> {
    /** トラックID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** トラック番号 */
    @NonNull
    private final Integer trackNo;
    /** トラックタイトル */
    @NonNull
    private final TrackTitle title;
    /** nullの場合はAlbumのartistCreditを継承 */
    @Nullable
    private final ArtistCredit artistCredit;
    /** トラック内のチューンのリスト */
    @NonNull
    private final List<TrackTune> tunes;

    /** trackNo必須違反時のエラー */
    private static final ErrorResult TRACK_NO_REQUIRED_ERROR = new ErrorResult(
            "trackNo",
            "Track number cannot be null",
            "TRACK_NO_REQUIRED");

    /** title必須違反時のエラー */
    private static final ErrorResult TITLE_REQUIRED_ERROR = new ErrorResult(
            "title",
            "Track title cannot be null",
            "TRACK_TITLE_REQUIRED");

    @DomainConstructor
    private Track(@NonNull Id id, @NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit, @NonNull List<TrackTune> tunes) {
        this.id = id;
        this.trackNo = trackNo;
        this.title = title;
        this.artistCredit = artistCredit;
        this.tunes = tunes;
    }

    @DomainFactory
    private static @NonNull Track factory(@Nullable Id id, @Nullable Integer trackNo, @Nullable TrackTitle title,
            @Nullable ArtistCredit artistCredit, @Nullable List<TrackTune> tunes) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.trackNo() != null,
                        TRACK_NO_REQUIRED_ERROR),
                Policy.of(
                        self -> self.title() != null,
                        TITLE_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                trackNo,
                                title,
                                artistCredit,
                                tunes),
                        Stub::asTrack)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, Integer trackNo, TrackTitle title, ArtistCredit artistCredit, List<TrackTune> tunes) {

        @AggregateFactory
        @NonNull
        Track asTrack() {
            return new Track(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(trackNo),
                    Objects.requireNonNull(title),
                    artistCredit(),
                    Objects.requireNonNull(tunes));
        }
    }

    /**
     * 新規トラックを生成
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @return 新規Track
     */
    @DomainFactory
    public static @NonNull Track create(@NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit) {
        return Track.factory(
                Id.generate(),
                trackNo,
                title,
                artistCredit,
                Collections.emptyList());
    }

    /**
     * 外部入力からトラックを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。{@code trackNo}・{@code title}の必須検証のみを
     * 担います。信頼できる内部生成には {@link #create} を使用してください。
     * </p>
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトルを表す文字列
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @return 成功時は {@code Track}、失敗時はエラー
     */
    public static Result<Track> fromInput(@Nullable Integer trackNo, @Nullable String title,
            @Nullable ArtistCredit artistCredit) {
        return Result.zip(
                Policy.<Integer>of(
                        Objects::nonNull,
                        () -> new ErrorResult(
                                "trackNo",
                                "Track number is required",
                                "TRACK_NO_REQUIRED"))
                        .verify(trackNo, Function.identity()),
                TrackTitle.fromInput(title),
                (validTrackNo, validTitle) -> Track.create(
                        validTrackNo,
                        validTitle,
                        artistCredit));
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            トラックID
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @param tunes
     *            チューンリスト
     * @return 再構成されたTrack
     */
    @DomainFactory
    public static @NonNull Track reconstruct(@NonNull Id id, @NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit, @NonNull List<TrackTune> tunes) {
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                tunes);
    }

    /**
     * トラックタイトルを変更
     *
     * @param newTitle
     *            新しいトラックタイトル
     * @return 更新されたTrack
     */
    public @NonNull Track changeTitle(@NonNull TrackTitle newTitle) {
        return Track.factory(
                id,
                trackNo,
                newTitle,
                artistCredit,
                tunes);
    }

    /**
     * アーティストクレジットを変更
     *
     * @param newArtistCredit
     *            新しいアーティストクレジット
     * @return 更新されたTrack
     */
    public @NonNull Track changeArtistCredit(@Nullable ArtistCredit newArtistCredit) {
        return Track.factory(
                id,
                trackNo,
                title,
                newArtistCredit,
                tunes);
    }

    /**
     * チューンを追加
     *
     * @param tune
     *            追加するチューン
     * @return 更新されたTrack
     */
    public @NonNull Track addTune(@NonNull TrackTune tune) {
        final var validatedTune = Policy.<TrackTune>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "tune",
                        "Tune cannot be null",
                        "TUNE_REQUIRED"))
                .verify(tune, Function.identity())
                .resolve(Policy::illegalArgument);
        Policy.<TrackTune>of(
                t -> tunes.stream().noneMatch(t::equivalentTo),
                () -> new ErrorResult(
                        "seq",
                        "Tune seq " + validatedTune.seq() + " already exists in this track",
                        "TUNE_SEQ_DUPLICATE"))
                .verify(validatedTune, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                Stream.concat(tunes.stream(), Stream.of(validatedTune)).toList());
    }

    /**
     * チューンを削除
     *
     * @param seq
     *            削除するチューンのseq
     * @return 更新されたTrack
     */
    public @NonNull Track removeTune(@NonNull Integer seq) {
        final var validatedSeq = Policy.<Integer>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "seq",
                        "Seq cannot be null",
                        "SEQ_REQUIRED"))
                .verify(seq, Function.identity())
                .resolve(Policy::illegalArgument);
        tunes.stream().filter(t -> t.hasId(validatedSeq)).findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("Tune with seq " + validatedSeq + " not found"));
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                tunes.stream().filter(not(t -> t.hasId(validatedSeq))).toList());
    }

    /**
     * チューンの上書き情報（クレジット上書き・リンクURL）を更新
     *
     * <p>
     * {@code tuneId}はトラックが表す構成の事実の一部として生成後は不変のため対象外とする。誤認識していたチューンの 再識別・訂正は
     * {@link #removeTune(Integer)} と {@link #addTune(TrackTune)} の組み合わせで表現する。
     * </p>
     *
     * @param seq
     *            対象チューンのシーケンス番号
     * @param composerCreditOverride
     *            新しい作曲者クレジット上書き（nullable）
     * @param arrangerCreditOverride
     *            新しいアレンジャークレジット上書き（nullable）
     * @param linkUrl
     *            新しいリンクURL（nullable）
     * @return 更新されたTrack
     */
    public @NonNull Track updateTune(@NonNull Integer seq, @Nullable Credit composerCreditOverride,
            @Nullable Credit arrangerCreditOverride, @Nullable Url linkUrl) {
        final var validatedSeq = Policy.<Integer>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "seq",
                        "Seq cannot be null",
                        "SEQ_REQUIRED"))
                .verify(seq, Function.identity())
                .resolve(Policy::illegalArgument);
        final var updatedTune = tunes.stream().filter(t -> t.hasId(validatedSeq)).findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("Tune with seq " + validatedSeq + " not found"))
                .changeComposerCreditOverride(composerCreditOverride)
                .changeArrangerCreditOverride(arrangerCreditOverride)
                .changeLinkUrl(linkUrl);
        return tunes.stream()
                .map(
                        t -> t.hasId(validatedSeq)
                                ? updatedTune
                                : t)
                .collect(optionally(toUnmodifiableList()))
                .map(
                        newTunes -> Track.factory(
                                id,
                                trackNo,
                                title,
                                artistCredit,
                                newTunes))
                .get();
    }

    /**
     * チューン構成を丸ごと置き換える
     *
     * <p>
     * トラックの更新（PUT風の全項目置換）が、入力の持つチューン構成をそのまま反映するための操作です。 {@code seq}
     * はトラック内で一意である必要があります。個別の追加・削除・更新は {@link #addTune(TrackTune)} /
     * {@link #removeTune(Integer)} / {@link #updateTune} で表現します。
     * </p>
     *
     * @param newTunes
     *            新しいチューン構成の一覧
     * @return 更新されたTrack
     */
    public @NonNull Track replaceTunes(@NonNull List<TrackTune> newTunes) {
        final var validatedTunes = Policy.<List<TrackTune>>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "tunes",
                        "Tunes cannot be null",
                        "TUNES_REQUIRED"))
                .verify(newTunes, Function.identity())
                .resolve(Policy::illegalArgument);
        Policy.<List<TrackTune>>of(
                Track::hasUniqueSeqs,
                () -> new ErrorResult(
                        "seq",
                        "Tune seq must be unique in this track",
                        "TUNE_SEQ_DUPLICATE"))
                .verify(validatedTunes, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
        return Track.factory(
                id,
                trackNo,
                title,
                artistCredit,
                List.copyOf(validatedTunes));
    }

    private static boolean hasUniqueSeqs(List<TrackTune> tunes) {
        return tunes.stream().map(TrackTune::seq).distinct().count() == tunes.size();
    }

    /**
     * チューンリストを取得（不変）
     *
     * @return チューンリストの不変コピー
     */
    public @NonNull List<TrackTune> getTunes() {
        return Collections.unmodifiableList(tunes);
    }

    @Override
    public @NonNull Id id() {
        return id;
    }

    /**
     * Track ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<Track> {
        public Id {
            idPolicy(value)
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        private static Policy<String> idPolicy(@Nullable String value) {
            return Policy.all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "Track ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult("value", "Track ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")));
        }

        /**
         * UUIDv7を生成してTrack.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からTrack.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }

        /**
         * 外部入力（文字列）からTrack.Idを生成します。
         *
         * <p>
         * 例外をスローせず、検証結果を {@link Result} で返します。 信頼できる内部生成には {@link #of(String)}
         * を使用してください。
         * </p>
         *
         * @param value
         *            ID値を表す文字列
         * @return 成功時は {@code Id}、失敗時はエラー
         */
        public static Result<Id> fromInput(@Nullable String value) {
            return idPolicy(value)
                    .verify(value, Id::new);
        }
    }
}
