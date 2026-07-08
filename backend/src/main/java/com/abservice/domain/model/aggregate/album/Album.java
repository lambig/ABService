package com.abservice.domain.model.aggregate.album;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
    @NonNull
    private final Id id;
    @NonNull
    private final AlbumTitle title;
    @NonNull
    private final BusinessDate releaseDate;
    @NonNull
    private final ArtistCredit artistCredit; // アルバム全体のアーティスト名義（必須）
    @Nullable
    private final EventReleasedAt eventReleasedAt; // nullable: イベント頒布情報が不明な場合
    @Nullable
    private final CatalogNumber catalogNumber; // nullable
    @Nullable
    private final Isdn isdn; // nullable: ISDN（国際標準同人誌番号）
    @NonNull
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
    public static @NonNull Album create(@NonNull AlbumTitle title, @NonNull BusinessDate releaseDate,
            @NonNull ArtistCredit artistCredit, @Nullable EventReleasedAt eventReleasedAt,
            @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn) {
        Optional.ofNullable(title).orElseThrow(() -> new IllegalArgumentException("Album title cannot be null"));
        Optional.ofNullable(artistCredit)
                .orElseThrow(() -> new IllegalArgumentException("Artist credit cannot be null"));
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
    @SuppressWarnings("checkstyle:ParameterNumber") // 永続化からの再構成で全項目を受け取るため引数が多い
    public static @NonNull Album reconstruct(@NonNull Id id, @NonNull AlbumTitle title,
            @NonNull BusinessDate releaseDate, @NonNull ArtistCredit artistCredit,
            @Nullable EventReleasedAt eventReleasedAt, @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn,
            @NonNull List<Track> tracks) {
        return new Album(id, title, releaseDate, artistCredit, eventReleasedAt, catalogNumber, isdn, tracks);
    }

    /**
     * アルバムタイトルを変更
     *
     * @param newTitle
     *            新しいアルバムタイトル
     * @return 更新されたAlbum
     */
    public @NonNull Album changeTitle(@NonNull AlbumTitle newTitle) {
        return withTitle(Optional.ofNullable(newTitle)
                .orElseThrow(() -> new IllegalArgumentException("Album title cannot be null")));
    }

    /**
     * リリース日を変更
     *
     * @param newReleaseDate
     *            新しいリリース日
     * @return 更新されたAlbum
     */
    public @NonNull Album changeReleaseDate(@NonNull BusinessDate newReleaseDate) {
        return withReleaseDate(newReleaseDate);
    }

    /**
     * アーティストクレジットを変更
     *
     * @param newArtistCredit
     *            新しいアーティストクレジット
     * @return 更新されたAlbum
     */
    public @NonNull Album changeArtistCredit(@NonNull ArtistCredit newArtistCredit) {
        return withArtistCredit(Optional.ofNullable(newArtistCredit)
                .orElseThrow(() -> new IllegalArgumentException("Artist credit cannot be null")));
    }

    /**
     * イベント頒布情報を変更
     *
     * @param newEventReleasedAt
     *            新しいイベント頒布情報
     * @return 更新されたAlbum
     */
    public @NonNull Album changeEventReleasedAt(@Nullable EventReleasedAt newEventReleasedAt) {
        return withEventReleasedAt(newEventReleasedAt);
    }

    /**
     * カタログナンバーを変更
     *
     * @param newCatalogNumber
     *            新しいカタログナンバー
     * @return 更新されたAlbum
     */
    public @NonNull Album changeCatalogNumber(@Nullable CatalogNumber newCatalogNumber) {
        return withCatalogNumber(newCatalogNumber);
    }

    /**
     * トラックを追加
     *
     * @param track
     *            追加するトラック
     * @return 更新されたAlbum
     */
    public @NonNull Album addTrack(@NonNull Track track) {
        final var validatedTrack = Optional.ofNullable(track)
                .orElseThrow(() -> new IllegalArgumentException("Track cannot be null"));
        // トラック番号の重複チェック
        tracks.stream().filter(t -> t.trackNo().equals(validatedTrack.trackNo())).findFirst().ifPresent(dup -> {
            throw new IllegalArgumentException("Track number " + validatedTrack.trackNo() + " already exists");
        });
        return withTracks(Stream.concat(tracks.stream(), Stream.of(validatedTrack)).toList());
    }

    /**
     * トラックを削除
     *
     * @param trackId
     *            削除するトラックのID
     * @return 更新されたAlbum
     */
    public @NonNull Album removeTrack(Track.@NonNull Id trackId) {
        final var validatedTrackId = Optional.ofNullable(trackId)
                .orElseThrow(() -> new IllegalArgumentException("Track ID cannot be null"));
        tracks.stream().filter(t -> t.hasId(validatedTrackId)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Track with ID " + validatedTrackId.value() + " not found"));
        return withTracks(tracks.stream().filter(t -> !t.hasId(validatedTrackId)).toList());
    }

    /**
     * トラックを更新
     *
     * @param updatedTrack
     *            更新するトラック
     * @return 更新されたAlbum
     */
    public @NonNull Album updateTrack(@NonNull Track updatedTrack) {
        final var validatedTrack = Optional.ofNullable(updatedTrack)
                .orElseThrow(() -> new IllegalArgumentException("Updated track cannot be null"));
        tracks.stream().filter(t -> t.equivalentTo(validatedTrack)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Track with ID " + validatedTrack.id().value() + " not found"));
        // トラック番号の重複チェック（自分自身以外）
        tracks.stream().filter(t -> !t.equivalentTo(validatedTrack))
                .filter(t -> t.trackNo().equals(validatedTrack.trackNo())).findFirst().ifPresent(dup -> {
                    throw new IllegalArgumentException("Track number " + validatedTrack.trackNo() + " already exists");
                });
        return withTracks(tracks.stream().map(t -> t.equivalentTo(validatedTrack)
                ? validatedTrack
                : t).toList());
    }

    /**
     * トラック順序を変更
     *
     * @param orderedTrackIds
     *            新しい順序のトラックIDリスト
     * @return 更新されたAlbum
     */
    public @NonNull Album reorderTracks(@NonNull List<Track.@NonNull Id> orderedTrackIds) {
        final var validatedIds = Optional.ofNullable(orderedTrackIds).filter(ids -> ids.size() == tracks.size())
                .orElseThrow(() -> new IllegalArgumentException("Ordered track IDs must match the number of tracks"));

        final var trackNo = new AtomicInteger(1);
        final var newTracks = validatedIds.stream().map(trackId -> {
            final var track = tracks.stream().filter(t -> t.hasId(trackId)).findFirst()
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
    public @NonNull List<Track> getTracksSortedByTrackNo() {
        return tracks.stream().sorted(Comparator.comparing(Track::trackNo)).toList();
    }

    /**
     * 特定のトラックを取得
     *
     * @param trackId
     *            トラックID
     * @return トラック
     */
    public @NonNull Track getTrack(Track.@NonNull Id trackId) {
        return tracks.stream().filter(t -> t.hasId(trackId)).findFirst()
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
    public @NonNull List<Track> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    @Override
    public @NonNull Id id() {
        return id;
    }

    /**
     * Album ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<Album> {
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
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からAlbum.Idを生成
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }
    }
}
