package com.abservice.domain.model.aggregate.album;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

/**
 * アルバム集約ルート
 *
 * <p>
 * アルバム、トラック、セット構成を管理する集約です。 トランザクション境界はこの集約全体に及びます。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Album implements Aggregate<Album, Album.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final AlbumTitle title;
    private final BusinessDate releaseDate;
    private final ArtistCredit artistCredit; // アルバム全体のアーティスト名義（必須）
    private final EventReleasedAt eventReleasedAt; // nullable: イベント頒布情報が不明な場合
    private final CatalogNumber catalogNumber; // nullable
    private final Isdn isdn; // nullable: ISDN（国際標準同人誌番号）
    private final List<Track> tracks;

    /**
     * 新規アルバムを生成
     *
     * @param title
     *            アルバムタイトル
     * @param releaseDate
     *            リリース日
     * @param artistCredit
     *            アーティストクレジット
     * @param eventReleasedAt
     *            イベント頒布情報（nullable）
     * @param catalogNumber
     *            カタログ番号（nullable）
     * @param isdn
     *            ISDN（nullable）
     * @return 新規Album
     */
    public static Album create(AlbumTitle title, BusinessDate releaseDate, ArtistCredit artistCredit,
            EventReleasedAt eventReleasedAt, CatalogNumber catalogNumber, Isdn isdn) {
        if (title == null) {
            throw new IllegalArgumentException("Album title cannot be null");
        }
        if (artistCredit == null) {
            throw new IllegalArgumentException("Artist credit cannot be null");
        }
        return new Album(Id.generate(), title, releaseDate, artistCredit, eventReleasedAt, catalogNumber, isdn,
                Collections.emptyList());
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            アルバムID
     * @param title
     *            アルバムタイトル
     * @param releaseDate
     *            リリース日
     * @param artistCredit
     *            アーティストクレジット
     * @param eventReleasedAt
     *            イベント頒布情報（nullable）
     * @param catalogNumber
     *            カタログ番号（nullable）
     * @param isdn
     *            ISDN（nullable）
     * @param tracks
     *            トラックリスト
     * @return 再構成されたAlbum
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Album reconstruct(Id id, AlbumTitle title, BusinessDate releaseDate, ArtistCredit artistCredit,
            EventReleasedAt eventReleasedAt, CatalogNumber catalogNumber, Isdn isdn, List<Track> tracks) {
        return new Album(id, title, releaseDate, artistCredit, eventReleasedAt, catalogNumber, isdn, tracks);
    }

    /**
     * アルバムタイトルを変更
     *
     * @param newTitle
     *            新しいアルバムタイトル
     * @return 更新されたAlbum
     */
    public Album changeTitle(AlbumTitle newTitle) {
        if (newTitle == null) {
            throw new IllegalArgumentException("Album title cannot be null");
        }
        return withTitle(newTitle);
    }

    /**
     * リリース日を変更
     *
     * @param newReleaseDate
     *            新しいリリース日
     * @return 更新されたAlbum
     */
    public Album changeReleaseDate(BusinessDate newReleaseDate) {
        return withReleaseDate(newReleaseDate);
    }

    /**
     * アーティストクレジットを変更
     *
     * @param newArtistCredit
     *            新しいアーティストクレジット
     * @return 更新されたAlbum
     */
    public Album changeArtistCredit(ArtistCredit newArtistCredit) {
        if (newArtistCredit == null) {
            throw new IllegalArgumentException("Artist credit cannot be null");
        }
        return withArtistCredit(newArtistCredit);
    }

    /**
     * イベント頒布情報を変更
     *
     * @param newEventReleasedAt
     *            新しいイベント頒布情報
     * @return 更新されたAlbum
     */
    public Album changeEventReleasedAt(EventReleasedAt newEventReleasedAt) {
        return withEventReleasedAt(newEventReleasedAt);
    }

    /**
     * カタログナンバーを変更
     *
     * @param newCatalogNumber
     *            新しいカタログナンバー
     * @return 更新されたAlbum
     */
    public Album changeCatalogNumber(CatalogNumber newCatalogNumber) {
        return withCatalogNumber(newCatalogNumber);
    }

    /**
     * トラックを追加
     *
     * @param track
     *            追加するトラック
     * @return 更新されたAlbum
     */
    public Album addTrack(Track track) {
        if (track == null) {
            throw new IllegalArgumentException("Track cannot be null");
        }
        // トラック番号の重複チェック
        if (tracks.stream().anyMatch(t -> t.trackNo().equals(track.trackNo()))) {
            throw new IllegalArgumentException("Track number " + track.trackNo() + " already exists");
        }
        var newTracks = new ArrayList<>(tracks);
        newTracks.add(track);
        return withTracks(Collections.unmodifiableList(newTracks));
    }

    /**
     * トラックを削除
     *
     * @param trackId
     *            削除するトラックのID
     * @return 更新されたAlbum
     */
    public Album removeTrack(Track.Id trackId) {
        if (trackId == null) {
            throw new IllegalArgumentException("Track ID cannot be null");
        }
        var newTracks = new ArrayList<>(tracks);
        var removed = newTracks.removeIf(t -> t.id().equals(trackId));
        if (!removed) {
            throw new IllegalArgumentException("Track with ID " + trackId.value() + " not found");
        }
        return withTracks(Collections.unmodifiableList(newTracks));
    }

    /**
     * トラックを更新
     *
     * @param updatedTrack
     *            更新するトラック
     * @return 更新されたAlbum
     */
    public Album updateTrack(Track updatedTrack) {
        if (updatedTrack == null) {
            throw new IllegalArgumentException("Updated track cannot be null");
        }
        var newTracks = new ArrayList<>(tracks);
        var index = newTracks.stream().filter(t -> t.id().equals(updatedTrack.id())).findFirst().map(newTracks::indexOf)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Track with ID " + updatedTrack.id().value() + " not found"));

        // トラック番号の重複チェック（自分自身以外）
        if (newTracks.stream().filter(t -> !t.id().equals(updatedTrack.id()))
                .anyMatch(t -> t.trackNo().equals(updatedTrack.trackNo()))) {
            throw new IllegalArgumentException("Track number " + updatedTrack.trackNo() + " already exists");
        }

        newTracks.set(index, updatedTrack);
        return withTracks(Collections.unmodifiableList(newTracks));
    }

    /**
     * トラック順序を変更
     *
     * @param orderedTrackIds
     *            新しい順序のトラックIDリスト
     * @return 更新されたAlbum
     */
    public Album reorderTracks(List<Track.Id> orderedTrackIds) {
        if (orderedTrackIds == null || orderedTrackIds.size() != tracks.size()) {
            throw new IllegalArgumentException("Ordered track IDs must match the number of tracks");
        }

        var trackNo = new java.util.concurrent.atomic.AtomicInteger(1);
        var newTracks = orderedTrackIds.stream().map(trackId -> {
            var track = tracks.stream().filter(t -> t.id().equals(trackId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Track with ID " + trackId.value() + " not found"));
            return track.withTrackNo(trackNo.getAndIncrement());
        }).toList();

        return withTracks(Collections.unmodifiableList(newTracks));
    }

    /**
     * トラックリストをトラック番号順にソートして取得
     *
     * @return トラック番号順にソートされたトラックリスト
     */
    public List<Track> getTracksSortedByTrackNo() {
        return tracks.stream().sorted(Comparator.comparing(Track::trackNo)).toList();
    }

    /**
     * 特定のトラックを取得
     *
     * @param trackId
     *            トラックID
     * @return トラック
     */
    public Track getTrack(Track.Id trackId) {
        return tracks.stream().filter(t -> t.id().equals(trackId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Track with ID " + trackId.value() + " not found"));
    }

    /**
     * トラック数を取得
     *
     * @return トラック数
     */
    public int getTrackCount() {
        return tracks.size();
    }

    /**
     * トラックリストを取得（不変）
     *
     * @return トラックリストの不変コピー
     */
    public List<Track> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    @Override
    public Id id() {
        return id;
    }

    /**
     * Album ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(String value) implements EntityId<Album> {
        public Id {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Album ID cannot be blank");
            }
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Album ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してAlbum.Idを作成
         */
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からAlbum.Idを生成
         */
        public static Id of(String value) {
            return new Id(value);
        }
    }
}
