package com.abservice.domain.model.aggregate.album;

import static com.abservice.lib.Iterables.toList;
import static java.util.function.Predicate.not;

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
        return new Album(Id.generate(), requireTitle(title), releaseDate, requireArtistCredit(artistCredit),
                eventReleasedAt, catalogNumber, isdn, Collections.emptyList());
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
        return new Album(
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
        return withTitle(requireTitle(newTitle));
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
        return withArtistCredit(requireArtistCredit(newArtistCredit));
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
        final var validatedTrack = Policy.<Track>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "track",
                        "Track cannot be null",
                        "TRACK_REQUIRED"))
                .verify(track, Function.identity())
                .resolve(Policy::illegalArgument);
        // トラック番号の重複チェック（ビジネスルール違反 → 409）
        tracks.stream().filter(t -> t.trackNo().equals(validatedTrack.trackNo())).findFirst().ifPresent(dup -> {
            throw new BusinessRuleViolationException("Track number " + validatedTrack.trackNo() + " already exists");
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
        return withTracks(tracks.stream().filter(not(t -> t.hasId(validatedTrackId))).toList());
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
        // トラック番号の重複チェック（自分自身以外。ビジネスルール違反 → 409）
        tracks.stream().filter(not(validatedTrack::equivalentTo))
                .filter(t -> t.trackNo().equals(validatedTrack.trackNo())).findFirst().ifPresent(dup -> {
                    throw new BusinessRuleViolationException(
                            "Track number " + validatedTrack.trackNo() + " already exists");
                });
        return withTracks(
                toList(
                        tracks,
                        t -> validatedTrack.equivalentTo(t)
                                ? validatedTrack
                                : t));
    }

    /**
     * トラック順序を変更
     *
     * @param orderedTrackIds
     *            新しい順序のトラックIDリスト
     * @return 更新されたAlbum
     */
    public @NonNull Album reorderTracks(@NonNull List<Track.@NonNull Id> orderedTrackIds) {
        return withTracks(
                Collections.unmodifiableList(
                        renumberByOrder(
                                Policy.<List<Track.Id>>of(
                                        ids -> Optional.ofNullable(ids)
                                                .filter(i -> i.size() == tracks.size())
                                                .isPresent(),
                                        () -> new ErrorResult(
                                                "orderedTrackIds",
                                                "Ordered track IDs must match the number of tracks",
                                                "TRACK_ORDER_SIZE_MISMATCH"))
                                        .verify(orderedTrackIds, Function.identity())
                                        .resolve(Policy::illegalArgument))));
    }

    private @NonNull List<Track> renumberByOrder(@NonNull List<Track.Id> orderedTrackIds) {
        final var trackNo = new AtomicInteger(1);
        return toList(
                orderedTrackIds,
                trackId -> getTrack(trackId)
                        .withTrackNo(trackNo.getAndIncrement()));
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

    private static @NonNull AlbumTitle requireTitle(@Nullable AlbumTitle title) {
        return Policy.<AlbumTitle>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "title",
                        "Album title cannot be null",
                        "ALBUM_TITLE_REQUIRED"))
                .verify(title, Function.identity())
                .resolve(Policy::illegalArgument);
    }

    private static @NonNull ArtistCredit requireArtistCredit(@Nullable ArtistCredit artistCredit) {
        return Policy.<ArtistCredit>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "artistCredit",
                        "Artist credit cannot be null",
                        "ARTIST_CREDIT_REQUIRED"))
                .verify(artistCredit, Function.identity())
                .resolve(Policy::illegalArgument);
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
