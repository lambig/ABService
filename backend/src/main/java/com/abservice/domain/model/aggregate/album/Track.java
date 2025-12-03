package com.abservice.domain.model.aggregate.album;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final Id id;
    private final Integer trackNo;
    private final TrackTitle title;
    private final ArtistCredit artistCredit; // nullable: nullの場合はAlbumのartistCreditを継承
    private final BusinessDate recordingDate;
    private final String recordingPlace;
    private final Boolean isLive;
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
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Track create(Integer trackNo, TrackTitle title, ArtistCredit artistCredit, BusinessDate recordingDate,
            String recordingPlace, Boolean isLive) {
        if (title == null) {
            throw new IllegalArgumentException("Track title cannot be null");
        }
        return new Track(Id.generate(), trackNo, title, artistCredit, recordingDate, recordingPlace, isLive,
                Collections.emptyList());
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
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Track reconstruct(Id id, Integer trackNo, TrackTitle title, ArtistCredit artistCredit,
            BusinessDate recordingDate, String recordingPlace, Boolean isLive, List<TrackTune> tunes) {
        return new Track(id, trackNo, title, artistCredit, recordingDate, recordingPlace, isLive, tunes);
    }

    /**
     * トラックタイトルを変更
     *
     * @param newTitle
     *            新しいトラックタイトル
     * @return 更新されたTrack
     */
    public Track changeTitle(TrackTitle newTitle) {
        if (newTitle == null) {
            throw new IllegalArgumentException("Track title cannot be null");
        }
        return withTitle(newTitle);
    }

    /**
     * アーティストクレジットを変更
     *
     * @param newArtistCredit
     *            新しいアーティストクレジット
     * @return 更新されたTrack
     */
    public Track changeArtistCredit(ArtistCredit newArtistCredit) {
        return withArtistCredit(newArtistCredit);
    }

    /**
     * 録音日を変更
     *
     * @param newRecordingDate
     *            新しい録音日
     * @return 更新されたTrack
     */
    public Track changeRecordingDate(BusinessDate newRecordingDate) {
        return withRecordingDate(newRecordingDate);
    }

    /**
     * 録音場所を変更
     *
     * @param newRecordingPlace
     *            新しい録音場所
     * @return 更新されたTrack
     */
    public Track changeRecordingPlace(String newRecordingPlace) {
        return withRecordingPlace(newRecordingPlace);
    }

    /**
     * ライブフラグを変更
     *
     * @param newIsLive
     *            新しいライブフラグ
     * @return 更新されたTrack
     */
    public Track changeIsLive(Boolean newIsLive) {
        return withIsLive(newIsLive);
    }

    /**
     * チューンを追加
     *
     * @param tune
     *            追加するチューン
     * @return 更新されたTrack
     */
    public Track addTune(TrackTune tune) {
        if (tune == null) {
            throw new IllegalArgumentException("Tune cannot be null");
        }
        // seqの重複チェック
        if (tunes.stream().anyMatch(t -> t.seq().equals(tune.seq()))) {
            throw new IllegalArgumentException("Tune seq " + tune.seq() + " already exists in this track");
        }
        var newTunes = new ArrayList<>(tunes);
        newTunes.add(tune);
        return withTunes(Collections.unmodifiableList(newTunes));
    }

    /**
     * チューンを削除
     *
     * @param seq
     *            削除するチューンのseq
     * @return 更新されたTrack
     */
    public Track removeTune(Integer seq) {
        if (seq == null) {
            throw new IllegalArgumentException("Seq cannot be null");
        }
        var newTunes = new ArrayList<>(tunes);
        var removed = newTunes.removeIf(t -> t.seq().equals(seq));
        if (!removed) {
            throw new IllegalArgumentException("Tune with seq " + seq + " not found");
        }
        return withTunes(Collections.unmodifiableList(newTunes));
    }

    /**
     * チューンを更新
     *
     * @param updatedTune
     *            更新するチューン
     * @return 更新されたTrack
     */
    public Track updateTune(TrackTune updatedTune) {
        if (updatedTune == null) {
            throw new IllegalArgumentException("Updated tune cannot be null");
        }
        var newTunes = new ArrayList<>(tunes);
        var index = newTunes.stream().filter(t -> t.seq().equals(updatedTune.seq())).findFirst().map(newTunes::indexOf)
                .orElseThrow(() -> new IllegalArgumentException("Tune with seq " + updatedTune.seq() + " not found"));
        newTunes.set(index, updatedTune);
        return withTunes(Collections.unmodifiableList(newTunes));
    }

    /**
     * チューンリストを取得（不変）
     *
     * @return チューンリストの不変コピー
     */
    public List<TrackTune> getTunes() {
        return Collections.unmodifiableList(tunes);
    }

    @Override
    public Id id() {
        return id;
    }

    /**
     * Track ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(String value) implements EntityId<Track> {
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
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からTrack.Idを生成
         */
        public static Id of(String value) {
            return new Id(value);
        }
    }
}
