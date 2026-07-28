package com.abservice.domain.model.aggregate.album;

import static com.abservice.lib.Optionals.optionally;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.lib.ErrorResult;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullUnmarked;

/**
 * アルバム集約ルート
 *
 * <p>
 * アルバム、トラック、セット構成を管理する集約です。 トランザクション境界はこの集約全体に及びます。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Album implements Aggregate<Album, Album.Id> {
    /** アルバムID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** アルバムタイトル */
    @NonNull
    private final AlbumTitle title;
    /** リリース日 */
    @NonNull
    private final BusinessDate releaseDate;
    /** アルバム全体のアーティスト名義 */
    @NonNull
    private final ArtistCredit artistCredit;
    /** イベント頒布情報 */
    @Nullable
    private final EventReleasedAt eventReleasedAt;
    /** カタログ番号 */
    @Nullable
    private final CatalogNumber catalogNumber;
    /** ISDN */
    @Nullable
    private final Isdn isdn;
    /** トラックのリスト */
    @NonNull
    private final List<Track> tracks;

    /** title必須違反時のエラー */
    private static final ErrorResult TITLE_REQUIRED_ERROR = new ErrorResult(
            "title",
            "Album title cannot be null",
            "ALBUM_TITLE_REQUIRED");

    /** artistCredit必須違反時のエラー */
    private static final ErrorResult ARTIST_CREDIT_REQUIRED_ERROR = new ErrorResult(
            "artistCredit",
            "Artist credit cannot be null",
            "ARTIST_CREDIT_REQUIRED");

    @SuppressWarnings("checkstyle:ParameterNumber") // PARAM-COUNT: 全フィールドを受け取る唯一の構築経路のため引数が多い
    private Album(@NonNull Id id, @NonNull AlbumTitle title, @NonNull BusinessDate releaseDate,
            @NonNull ArtistCredit artistCredit, @Nullable EventReleasedAt eventReleasedAt,
            @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn, @NonNull List<Track> tracks) {
        this.id = id;
        this.title = title;
        this.releaseDate = releaseDate;
        this.artistCredit = artistCredit;
        this.eventReleasedAt = eventReleasedAt;
        this.catalogNumber = catalogNumber;
        this.isdn = isdn;
        this.tracks = tracks;
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // PARAM-COUNT: 全項目を受け取るため引数が多い
    private static @NonNull Album factory(@Nullable Id id, @Nullable AlbumTitle title,
            @Nullable BusinessDate releaseDate, @Nullable ArtistCredit artistCredit,
            @Nullable EventReleasedAt eventReleasedAt, @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn,
            @Nullable List<Track> tracks) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.title() != null,
                        TITLE_REQUIRED_ERROR),
                Policy.of(
                        self -> self.artistCredit() != null,
                        ARTIST_CREDIT_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                title,
                                releaseDate,
                                artistCredit,
                                eventReleasedAt,
                                catalogNumber,
                                isdn,
                                tracks),
                        Stub::asAlbum)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, AlbumTitle title, BusinessDate releaseDate, ArtistCredit artistCredit,
            EventReleasedAt eventReleasedAt, CatalogNumber catalogNumber, Isdn isdn, List<Track> tracks) {

        @AggregateFactory
        @NonNull
        Album asAlbum() {
            return new Album(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(title),
                    Objects.requireNonNull(releaseDate),
                    Objects.requireNonNull(artistCredit),
                    eventReleasedAt(),
                    catalogNumber(),
                    isdn(),
                    Objects.requireNonNull(tracks));
        }
    }

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
        return Album.factory(
                Id.generate(),
                title,
                releaseDate,
                artistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
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
    @SuppressWarnings("checkstyle:ParameterNumber") // PARAM-COUNT: 永続化からの再構成で全項目を受け取るため引数が多い
    public static @NonNull Album reconstruct(@NonNull Id id, @NonNull AlbumTitle title,
            @NonNull BusinessDate releaseDate, @NonNull ArtistCredit artistCredit,
            @Nullable EventReleasedAt eventReleasedAt, @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn,
            @NonNull List<Track> tracks) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
                tracks);
    }

    /**
     * アルバムタイトルを変更
     *
     * @param newTitle
     *            新しいアルバムタイトル
     * @return 更新されたAlbum
     */
    public @NonNull Album changeTitle(@NonNull AlbumTitle newTitle) {
        return Album.factory(
                id,
                newTitle,
                releaseDate,
                artistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
                tracks);
    }

    /**
     * リリース日を変更
     *
     * @param newReleaseDate
     *            新しいリリース日
     * @return 更新されたAlbum
     */
    public @NonNull Album changeReleaseDate(@NonNull BusinessDate newReleaseDate) {
        return Album.factory(
                id,
                title,
                newReleaseDate,
                artistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
                tracks);
    }

    /**
     * アーティストクレジットを変更
     *
     * @param newArtistCredit
     *            新しいアーティストクレジット
     * @return 更新されたAlbum
     */
    public @NonNull Album changeArtistCredit(@NonNull ArtistCredit newArtistCredit) {
        return Album.factory(
                id,
                title,
                releaseDate,
                newArtistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
                tracks);
    }

    /**
     * イベント頒布情報を変更
     *
     * @param newEventReleasedAt
     *            新しいイベント頒布情報
     * @return 更新されたAlbum
     */
    public @NonNull Album changeEventReleasedAt(@Nullable EventReleasedAt newEventReleasedAt) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                newEventReleasedAt,
                catalogNumber,
                isdn,
                tracks);
    }

    /**
     * カタログナンバーを変更
     *
     * @param newCatalogNumber
     *            新しいカタログナンバー
     * @return 更新されたAlbum
     */
    public @NonNull Album changeCatalogNumber(@Nullable CatalogNumber newCatalogNumber) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                eventReleasedAt,
                newCatalogNumber,
                isdn,
                tracks);
    }

    /**
     * トラックを追加
     *
     * @param track
     *            追加するトラック
     * @return 更新されたAlbum
     */
    public @NonNull Album addTrack(@NonNull Track track) {
        final var validatedTrack = Policy.<Track>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "track",
                        "Track cannot be null",
                        "TRACK_REQUIRED"))
                .verify(track, Function.identity())
                .resolve(Policy::illegalArgument);
        tracks.stream().filter(t -> t.trackNo().equals(validatedTrack.trackNo())).findFirst().ifPresent(dup -> {
            throw new BusinessRuleViolationException("Track number " + validatedTrack.trackNo() + " already exists");
        });
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
                Stream.concat(tracks.stream(), Stream.of(validatedTrack)).toList());
    }

    /**
     * トラックを削除
     *
     * @param trackId
     *            削除するトラックのID
     * @return 更新されたAlbum
     */
    public @NonNull Album removeTrack(Track.@NonNull Id trackId) {
        final var validatedTrackId = Policy.<Track.Id>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "trackId",
                        "Track ID cannot be null",
                        "TRACK_ID_REQUIRED"))
                .verify(trackId, Function.identity())
                .resolve(Policy::illegalArgument);
        tracks.stream().filter(t -> t.hasId(validatedTrackId)).findFirst().orElseThrow(
                () -> new BusinessRuleViolationException("Track with ID " + validatedTrackId.value() + " not found"));
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
                tracks.stream().filter(not(t -> t.hasId(validatedTrackId))).toList());
    }

    /**
     * トラックを更新
     *
     * @param updatedTrack
     *            更新するトラック
     * @return 更新されたAlbum
     */
    public @NonNull Album updateTrack(@NonNull Track updatedTrack) {
        final var validatedTrack = Policy.<Track>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "updatedTrack",
                        "Updated track cannot be null",
                        "TRACK_REQUIRED"))
                .verify(updatedTrack, Function.identity())
                .resolve(Policy::illegalArgument);
        tracks.stream().filter(validatedTrack::equivalentTo).findFirst().orElseThrow(
                () -> new BusinessRuleViolationException(
                        "Track with ID " + validatedTrack.id().value() + " not found"));
        tracks.stream().filter(not(validatedTrack::equivalentTo))
                .filter(t -> t.trackNo().equals(validatedTrack.trackNo())).findFirst().ifPresent(dup -> {
                    throw new BusinessRuleViolationException(
                            "Track number " + validatedTrack.trackNo() + " already exists");
                });
        return tracks.stream()
                .map(
                        t -> validatedTrack.equivalentTo(t)
                                ? validatedTrack
                                : t)
                .collect(optionally(toUnmodifiableList()))
                .map(
                        newTracks -> Album.factory(
                                id,
                                title,
                                releaseDate,
                                artistCredit,
                                eventReleasedAt,
                                catalogNumber,
                                isdn,
                                newTracks))
                .get();
    }

    /**
     * トラック順序を変更
     *
     * @param orderedTrackIds
     *            新しい順序のトラックIDリスト
     * @return 更新されたAlbum
     */
    public @NonNull Album reorderTracks(@NonNull List<Track.@NonNull Id> orderedTrackIds) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                eventReleasedAt,
                catalogNumber,
                isdn,
                Collections.unmodifiableList(renumberByOrder(validateOrderedTrackIds(orderedTrackIds))));
    }

    private @NonNull List<Track.Id> validateOrderedTrackIds(@NonNull List<Track.@NonNull Id> orderedTrackIds) {
        return Policy.<List<Track.Id>>of(
                ids -> Optional.ofNullable(ids)
                        .filter(i -> i.size() == tracks.size())
                        .isPresent(),
                () -> new ErrorResult(
                        "orderedTrackIds",
                        "Ordered track IDs must match the number of tracks",
                        "TRACK_ORDER_SIZE_MISMATCH"))
                .verify(orderedTrackIds, Function.identity())
                .resolve(Policy::illegalArgument);
    }

    private @NonNull List<Track> renumberByOrder(@NonNull List<Track.Id> orderedTrackIds) {
        final var trackNo = new AtomicInteger(1);
        return orderedTrackIds.stream()
                .map(this::getTrack)
                .map(
                        track -> Track.reconstruct(
                                track.id(),
                                trackNo.getAndIncrement(),
                                track.title(),
                                track.artistCredit(),
                                track.recordingDate(),
                                track.recordingPlace(),
                                track.isLive(),
                                track.getTunes()))
                .toList();
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
                .orElseThrow(
                        () -> new BusinessRuleViolationException("Track with ID " + trackId.value() + " not found"));
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
            Policy.<String>all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "Album ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult("value", "Album ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")))
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        /**
         * UUIDv7を生成してAlbum.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からAlbum.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }
    }
}
