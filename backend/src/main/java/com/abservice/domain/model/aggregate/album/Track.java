package com.abservice.domain.model.aggregate.album;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Track implements DomainEntity<Track, Track.Id> {
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
        return new Track(Id.generate(),
                Optional.ofNullable(trackNo)
                        .orElseThrow(() -> new IllegalArgumentException("Track number cannot be null")),
                Optional.ofNullable(title)
                        .orElseThrow(() -> new IllegalArgumentException("Track title cannot be null")),
                artistCredit, recordingDate, recordingPlace, isLive, Collections.emptyList());
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
        return create(trackNo, title, artistCredit, recordingDate, null, null);
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
        return new Track(id, trackNo, title, artistCredit, recordingDate, recordingPlace, isLive, tunes);
    }

    /**
     * トラックタイトルを変更
     *
     * @param newTitle
     *            新しいトラックタイトル
     * @return 更新されたTrack
     */
    public @NonNull Track changeTitle(@NonNull TrackTitle newTitle) {
        return withTitle(Optional.ofNullable(newTitle)
                .orElseThrow(() -> new IllegalArgumentException("Track title cannot be null")));
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
        var validatedTune = Optional.ofNullable(tune)
                .orElseThrow(() -> new IllegalArgumentException("Tune cannot be null"));
        // seqの重複チェック
        if (tunes.stream().anyMatch(t -> t.seq().equals(validatedTune.seq()))) {
            throw new IllegalArgumentException("Tune seq " + validatedTune.seq() + " already exists in this track");
        }
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
        var validatedSeq = Optional.ofNullable(seq)
                .orElseThrow(() -> new IllegalArgumentException("Seq cannot be null"));
        if (tunes.stream().noneMatch(t -> t.seq().equals(validatedSeq))) {
            throw new IllegalArgumentException("Tune with seq " + validatedSeq + " not found");
        }
        return withTunes(tunes.stream().filter(t -> !t.seq().equals(validatedSeq)).toList());
    }

    /**
     * チューンを更新
     *
     * @param updatedTune
     *            更新するチューン
     * @return 更新されたTrack
     */
    public @NonNull Track updateTune(@NonNull TrackTune updatedTune) {
        var validatedTune = Optional.ofNullable(updatedTune)
                .orElseThrow(() -> new IllegalArgumentException("Updated tune cannot be null"));
        if (tunes.stream().noneMatch(t -> t.seq().equals(validatedTune.seq()))) {
            throw new IllegalArgumentException("Tune with seq " + validatedTune.seq() + " not found");
        }
        return withTunes(tunes.stream().map(t -> t.seq().equals(validatedTune.seq()) ? validatedTune : t).toList());
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
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Track ID cannot be blank");
            }
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Track ID must be a valid UUID: " + value);
            }
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
