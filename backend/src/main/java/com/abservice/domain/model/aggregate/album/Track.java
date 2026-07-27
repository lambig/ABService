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
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

/**
 * トラック（集約内エンティティ）
 *
 * <p>
 * アルバム内の1トラックの録音を表します。 録音違い（スタジオ版/ライブ版など）は別Trackとして扱います。
 * </p>
 */
@With(AccessLevel.PACKAGE)
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Track implements DomainEntity<Track, Track.Id> {
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    @NonNull
    private final Integer trackNo;
    @NonNull
    private final TrackTitle title;
    @Nullable
    private final ArtistCredit artistCredit; // nullable: nullの場合はAlbumのartistCreditを継承
    @Nullable
    private final BusinessDate recordingDate;
    @Nullable
    private final String recordingPlace;
    @Nullable
    private final Boolean isLive;
    @NonNull
    private final List<TrackTune> tunes;

    // 全フィールドを受け取る唯一の構築経路（@Withが生成するwitherも本コンストラクタを呼ぶ）。
    // Policy検証をここに一本化することで、witherを含むどの経路からも検証を迂回できない（#101）。
    @SuppressWarnings("checkstyle:ParameterNumber") // 全フィールドを受け取る唯一の構築経路のため引数が多い
    private Track(@NonNull Id id, @NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit, @Nullable BusinessDate recordingDate,
            @Nullable String recordingPlace, @Nullable Boolean isLive, @NonNull List<TrackTune> tunes) {
        this.id = id;
        this.trackNo = requireTrackNo(trackNo);
        this.title = requireTitle(title);
        this.artistCredit = artistCredit;
        this.recordingDate = recordingDate;
        this.recordingPlace = recordingPlace;
        this.isLive = isLive;
        this.tunes = tunes;
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
     * @param recordingDate
     *            録音日
     * @param recordingPlace
     *            録音場所
     * @param duration
     *            再生時間
     * @param isLive
     *            ライブフラグ
     * @return 新規Track
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // 生成に必要な全項目を受け取るため引数が多い
    public static @NonNull Track create(@NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit, @Nullable BusinessDate recordingDate, @Nullable String recordingPlace,
            @Nullable Boolean isLive) {
        return new Track(Id.generate(), trackNo, title, artistCredit, recordingDate,
                recordingPlace, isLive, Collections.emptyList());
    }

    /**
     * 新規トラックを生成（簡略版）
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistCredit
     *            アーティストクレジット（nullable）
     * @param recordingDate
     *            録音日（nullable）
     * @return 新規Track
     */
    public static @NonNull Track create(@NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit, @Nullable BusinessDate recordingDate) {
        return create(
                trackNo,
                title,
                artistCredit,
                recordingDate,
                null,
                null);
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
     * @param recordingDate
     *            録音日
     * @param recordingPlace
     *            録音場所
     * @param isLive
     *            ライブフラグ
     * @param tunes
     *            チューンリスト
     * @return 再構成されたTrack
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // 永続化からの再構成で全項目を受け取るため引数が多い
    public static @NonNull Track reconstruct(@NonNull Id id, @NonNull Integer trackNo, @NonNull TrackTitle title,
            @Nullable ArtistCredit artistCredit, @Nullable BusinessDate recordingDate, @Nullable String recordingPlace,
            @Nullable Boolean isLive, @NonNull List<TrackTune> tunes) {
        return new Track(
                id,
                trackNo,
                title,
                artistCredit,
                recordingDate,
                recordingPlace,
                isLive,
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
        return new Track(
                id,
                trackNo,
                newTitle,
                artistCredit,
                recordingDate,
                recordingPlace,
                isLive,
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
        return withArtistCredit(newArtistCredit);
    }

    /**
     * 録音日を変更
     *
     * @param newRecordingDate
     *            新しい録音日
     * @return 更新されたTrack
     */
    public @NonNull Track changeRecordingDate(@Nullable BusinessDate newRecordingDate) {
        return withRecordingDate(newRecordingDate);
    }

    /**
     * 録音場所を変更
     *
     * @param newRecordingPlace
     *            新しい録音場所
     * @return 更新されたTrack
     */
    public @NonNull Track changeRecordingPlace(@Nullable String newRecordingPlace) {
        return withRecordingPlace(newRecordingPlace);
    }

    /**
     * ライブフラグを変更
     *
     * @param newIsLive
     *            新しいライブフラグ
     * @return 更新されたTrack
     */
    public @NonNull Track changeIsLive(@Nullable Boolean newIsLive) {
        return withIsLive(newIsLive);
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
        // seqの重複チェック（ビジネスルール違反 → 409）
        tunes.stream().filter(validatedTune::equivalentTo).findFirst().ifPresent(dup -> {
            throw new BusinessRuleViolationException(
                    "Tune seq " + validatedTune.seq() + " already exists in this track");
        });
        return withTunes(Stream.concat(tunes.stream(), Stream.of(validatedTune)).toList());
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
        return withTunes(tunes.stream().filter(not(t -> t.hasId(validatedSeq))).toList());
    }

    /**
     * チューンの上書き情報（クレジット上書き・リンクURL）を更新
     *
     * <p>
     * {@code tuneId}は録音実績の一部として生成後は不変のため対象外とする。誤認識していたチューンの 再識別・訂正は
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
                .map(this::withTunes)
                .get();
    }

    /**
     * チューンリストを取得（不変）
     *
     * @return チューンリストの不変コピー
     */
    public @NonNull List<TrackTune> getTunes() {
        return Collections.unmodifiableList(tunes);
    }

    private static @NonNull Integer requireTrackNo(@Nullable Integer trackNo) {
        return Policy.<Integer>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "trackNo",
                        "Track number cannot be null",
                        "TRACK_NO_REQUIRED"))
                .verify(trackNo, Function.identity())
                .resolve(Policy::illegalArgument);
    }

    private static @NonNull TrackTitle requireTitle(@Nullable TrackTitle title) {
        return Policy.<TrackTitle>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "title",
                        "Track title cannot be null",
                        "TRACK_TITLE_REQUIRED"))
                .verify(title, Function.identity())
                .resolve(Policy::illegalArgument);
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
            Policy.<String>all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "Track ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult("value", "Track ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")))
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        /**
         * UUIDv7を生成してTrack.Idを作成
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からTrack.Idを生成
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }
    }
}
