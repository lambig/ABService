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
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.Publication;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.AssetKey;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.ExternalAudioUrl;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
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
    /**
     * 作品の概要説明（Null Objectパターン。説明なしは {@code MarkupContent.EMPTY}）
     *
     * <p>
     * この作品が何であるかを述べるストック情報であり、頒布の告知や制作の経緯といった時点の記述は記事側が持つ。
     * </p>
     */
    @NonNull
    private final MarkupContent description;
    /** イベント頒布情報 */
    @Nullable
    private final EventReleasedAt eventReleasedAt;
    /** カタログ番号 */
    @Nullable
    private final CatalogNumber catalogNumber;
    /** ISDN */
    @Nullable
    private final Isdn isdn;
    /** カバー画像のアセットキー（配信URLは照会時に配信設定から組み立てる） */
    @Nullable
    private final AssetKey coverImageKey;
    /**
     * 公開情報（Null
     * Objectパターン。{@code Publication.Draft}=下書き、{@code Publication.Published}=公開中）
     */
    @NonNull
    private final Publication publication;
    /** トラックのリスト */
    @NonNull
    private final List<Track> tracks;
    /** 外部音源（外部サービスの埋め込み）のリスト */
    @NonNull
    private final List<ExternalAudio> externalAudios;

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

    /** publication必須違反時のエラー */
    private static final ErrorResult PUBLICATION_REQUIRED_ERROR = new ErrorResult(
            "publication",
            "Publication cannot be null",
            "PUBLICATION_REQUIRED");

    /** description必須違反時のエラー */
    private static final ErrorResult DESCRIPTION_REQUIRED_ERROR = new ErrorResult(
            "description",
            "Description cannot be null",
            "ALBUM_DESCRIPTION_REQUIRED");

    @DomainConstructor
    private Album(@NonNull Id id, @NonNull AlbumTitle title, @NonNull BusinessDate releaseDate,
            @NonNull ArtistCredit artistCredit, @NonNull MarkupContent description,
            @Nullable EventReleasedAt eventReleasedAt,
            @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn, @Nullable AssetKey coverImageKey,
            @NonNull Publication publication, @NonNull List<Track> tracks,
            @NonNull List<ExternalAudio> externalAudios) {
        this.id = id;
        this.title = title;
        this.releaseDate = releaseDate;
        this.artistCredit = artistCredit;
        this.description = description;
        this.eventReleasedAt = eventReleasedAt;
        this.catalogNumber = catalogNumber;
        this.isdn = isdn;
        this.coverImageKey = coverImageKey;
        this.publication = publication;
        this.tracks = tracks;
        this.externalAudios = externalAudios;
    }

    @DomainFactory
    private static @NonNull Album factory(@Nullable Id id, @Nullable AlbumTitle title,
            @Nullable BusinessDate releaseDate, @Nullable ArtistCredit artistCredit,
            @Nullable MarkupContent description,
            @Nullable EventReleasedAt eventReleasedAt, @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn,
            @Nullable AssetKey coverImageKey, @Nullable Publication publication, @Nullable List<Track> tracks,
            @Nullable List<ExternalAudio> externalAudios) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.title() != null,
                        TITLE_REQUIRED_ERROR),
                Policy.of(
                        self -> self.artistCredit() != null,
                        ARTIST_CREDIT_REQUIRED_ERROR),
                Policy.of(
                        self -> self.publication() != null,
                        PUBLICATION_REQUIRED_ERROR),
                Policy.of(
                        self -> self.description() != null,
                        DESCRIPTION_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                title,
                                releaseDate,
                                artistCredit,
                                description,
                                eventReleasedAt,
                                catalogNumber,
                                isdn,
                                coverImageKey,
                                publication,
                                tracks,
                                externalAudios),
                        Stub::asAlbum)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, AlbumTitle title, BusinessDate releaseDate, ArtistCredit artistCredit,
            MarkupContent description, EventReleasedAt eventReleasedAt, CatalogNumber catalogNumber, Isdn isdn,
            AssetKey coverImageKey, Publication publication, List<Track> tracks,
            List<ExternalAudio> externalAudios) {

        @AggregateFactory
        @NonNull
        Album asAlbum() {
            return new Album(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(title),
                    Objects.requireNonNull(releaseDate),
                    Objects.requireNonNull(artistCredit),
                    Objects.requireNonNull(description),
                    eventReleasedAt(),
                    catalogNumber(),
                    isdn(),
                    coverImageKey(),
                    Objects.requireNonNull(publication),
                    Objects.requireNonNull(tracks),
                    Objects.requireNonNull(externalAudios));
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
     * @param description
     *            概要説明（説明なしは {@code MarkupContent.EMPTY}）
     * @param eventReleasedAt
     *            イベント頒布情報（nullable）
     * @param catalogNumber
     *            カタログ番号（nullable）
     * @param isdn
     *            ISDN（nullable）
     * @param coverImageKey
     *            カバー画像のアセットキー（nullable）
     * @return 新規Album
     */
    @DomainFactory
    public static @NonNull Album create(@NonNull AlbumTitle title, @NonNull BusinessDate releaseDate,
            @NonNull ArtistCredit artistCredit, @NonNull MarkupContent description,
            @Nullable EventReleasedAt eventReleasedAt,
            @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn, @Nullable AssetKey coverImageKey) {
        return Album.factory(
                Id.generate(),
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                Publication.draft(),
                Collections.emptyList(),
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
     * @param description
     *            概要説明（説明なしは {@code MarkupContent.EMPTY}）
     * @param eventReleasedAt
     *            イベント頒布情報（nullable）
     * @param catalogNumber
     *            カタログ番号（nullable）
     * @param isdn
     *            ISDN（nullable）
     * @param coverImageKey
     *            カバー画像のアセットキー（nullable）
     * @param publication
     *            公開情報（non-null。{@code Publication.draft()}=下書き）
     * @param tracks
     *            トラックリスト
     * @param externalAudios
     *            外部音源リスト
     * @return 再構成されたAlbum
     */
    @DomainFactory
    public static @NonNull Album reconstruct(@NonNull Id id, @NonNull AlbumTitle title,
            @NonNull BusinessDate releaseDate, @NonNull ArtistCredit artistCredit,
            @NonNull MarkupContent description,
            @Nullable EventReleasedAt eventReleasedAt, @Nullable CatalogNumber catalogNumber, @Nullable Isdn isdn,
            @Nullable AssetKey coverImageKey, @NonNull Publication publication, @NonNull List<Track> tracks,
            @NonNull List<ExternalAudio> externalAudios) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
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
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
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
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
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
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
    }

    /**
     * 概要説明を変更
     *
     * @param newDescription
     *            新しい概要説明（説明なしにする場合は {@code MarkupContent.EMPTY}）
     * @return 更新されたAlbum
     */
    public @NonNull Album changeDescription(@NonNull MarkupContent newDescription) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                newDescription,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
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
                description,
                newEventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
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
                description,
                eventReleasedAt,
                newCatalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
    }

    /**
     * ISDNを変更
     *
     * @param newIsdn
     *            新しいISDN
     * @return 更新されたAlbum
     */
    public @NonNull Album changeIsdn(@Nullable Isdn newIsdn) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                newIsdn,
                coverImageKey,
                publication,
                tracks,
                externalAudios);
    }

    /**
     * カバー画像を変更
     *
     * @param newCoverImageKey
     *            新しいカバー画像のアセットキー（nullable。null でカバー画像なしにする）
     * @return 更新されたAlbum
     */
    public @NonNull Album changeCoverImageKey(@Nullable AssetKey newCoverImageKey) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                newCoverImageKey,
                publication,
                tracks,
                externalAudios);
    }

    /**
     * アルバムを公開
     *
     * <p>
     * 既に公開中の場合は最初に公開した日時を据え置く（再公開で公開日時が繰り下がらない）。
     * </p>
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新されたAlbum
     */
    public @NonNull Album publish(@NonNull BusinessDateTime currentDateTime) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                Publication.published(publication.publishedAt().orElse(currentDateTime)),
                tracks,
                externalAudios);
    }

    /**
     * アルバムを非公開化
     *
     * @return 更新されたAlbum
     */
    public @NonNull Album unpublish() {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                Publication.draft(),
                tracks,
                externalAudios);
    }

    /**
     * 公開中かどうか
     *
     * @return 公開中の場合true
     */
    public boolean isPublished() {
        return publication.isPublished();
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
        Policy.<Track>of(
                t -> tracks.stream().noneMatch(existing -> existing.trackNo().equals(t.trackNo())),
                () -> new ErrorResult(
                        "trackNo",
                        "Track number " + validatedTrack.trackNo() + " already exists",
                        "TRACK_NO_DUPLICATE"))
                .verify(validatedTrack, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                Stream.concat(tracks.stream(), Stream.of(validatedTrack)).toList(),
                externalAudios);
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
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks.stream().filter(not(t -> t.hasId(validatedTrackId))).toList(),
                externalAudios);
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
        Policy.<Track>of(
                t -> tracks.stream().filter(not(t::equivalentTo))
                        .noneMatch(existing -> existing.trackNo().equals(t.trackNo())),
                () -> new ErrorResult(
                        "trackNo",
                        "Track number " + validatedTrack.trackNo() + " already exists",
                        "TRACK_NO_DUPLICATE"))
                .verify(validatedTrack, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
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
                                description,
                                eventReleasedAt,
                                catalogNumber,
                                isdn,
                                coverImageKey,
                                publication,
                                newTracks,
                                externalAudios))
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
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                Collections.unmodifiableList(renumberByOrder(validateOrderedTrackIds(orderedTrackIds))),
                externalAudios);
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
                .resolve(BusinessRuleViolationException::fromErrors);
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

    /**
     * 外部音源を追加
     *
     * <p>
     * 表示順は末尾に採番します。同一URLの重複登録は業務違反として拒否します。
     * </p>
     *
     * @param url
     *            追加する外部音源の埋め込み元URL
     * @return 追加後のアルバムと追加された外部音源の組
     */
    public @NonNull ExternalAudioAddition addExternalAudio(@NonNull ExternalAudioUrl url) {
        final var validatedUrl = Policy.<ExternalAudioUrl>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "url",
                        "External audio URL cannot be null",
                        "EXTERNAL_AUDIO_URL_REQUIRED"))
                .verify(url, Function.identity())
                .resolve(Policy::illegalArgument);
        Policy.<ExternalAudioUrl>of(
                u -> externalAudios.stream().noneMatch(existing -> existing.hasUrl(u)),
                () -> new ErrorResult(
                        "url",
                        "External audio " + validatedUrl.value().value() + " already exists",
                        "EXTERNAL_AUDIO_URL_DUPLICATE"))
                .verify(validatedUrl, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
        final var added = ExternalAudio.create(externalAudios.size() + 1, validatedUrl);
        return new ExternalAudioAddition(
                Album.factory(
                        id,
                        title,
                        releaseDate,
                        artistCredit,
                        description,
                        eventReleasedAt,
                        catalogNumber,
                        isdn,
                        coverImageKey,
                        publication,
                        tracks,
                        Stream.concat(externalAudios.stream(), Stream.of(added)).toList()),
                added);
    }

    /**
     * 外部音源を削除
     *
     * <p>
     * 残る外部音源の表示順は1から詰め直します（表示順は並びの表現でしかなく、欠番に意味がないため）。
     * </p>
     *
     * @param externalAudioId
     *            削除する外部音源のID
     * @return 更新されたAlbum
     */
    public @NonNull Album removeExternalAudio(ExternalAudio.@NonNull Id externalAudioId) {
        final var validatedId = Policy.<ExternalAudio.Id>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "externalAudioId",
                        "External audio ID cannot be null",
                        "EXTERNAL_AUDIO_ID_REQUIRED"))
                .verify(externalAudioId, Function.identity())
                .resolve(Policy::illegalArgument);
        externalAudios.stream().filter(a -> a.hasId(validatedId)).findFirst().orElseThrow(
                () -> new BusinessRuleViolationException(
                        "External audio with ID " + validatedId.value() + " not found"));
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                renumberSequentially(externalAudiosExcluding(validatedId)));
    }

    private @NonNull List<ExternalAudio> externalAudiosExcluding(ExternalAudio.@NonNull Id excludedId) {
        return externalAudios.stream().filter(not(a -> a.hasId(excludedId))).toList();
    }

    /**
     * 外部音源の表示順を変更
     *
     * @param orderedExternalAudioIds
     *            新しい順序の外部音源IDリスト
     * @return 更新されたAlbum
     */
    public @NonNull Album reorderExternalAudios(
            @NonNull List<ExternalAudio.@NonNull Id> orderedExternalAudioIds) {
        return Album.factory(
                id,
                title,
                releaseDate,
                artistCredit,
                description,
                eventReleasedAt,
                catalogNumber,
                isdn,
                coverImageKey,
                publication,
                tracks,
                Collections.unmodifiableList(
                        renumberExternalAudiosByOrder(
                                validateOrderedExternalAudioIds(orderedExternalAudioIds))));
    }

    private @NonNull List<ExternalAudio.Id> validateOrderedExternalAudioIds(
            @NonNull List<ExternalAudio.@NonNull Id> orderedExternalAudioIds) {
        return Policy.<List<ExternalAudio.Id>>of(
                ids -> Optional.ofNullable(ids)
                        .filter(i -> i.size() == externalAudios.size())
                        .isPresent(),
                () -> new ErrorResult(
                        "orderedExternalAudioIds",
                        "Ordered external audio IDs must match the number of external audios",
                        "EXTERNAL_AUDIO_ORDER_SIZE_MISMATCH"))
                .verify(orderedExternalAudioIds, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
    }

    private @NonNull List<ExternalAudio> renumberExternalAudiosByOrder(
            @NonNull List<ExternalAudio.Id> orderedExternalAudioIds) {
        return orderedExternalAudioIds.stream()
                .map(this::getExternalAudio)
                .collect(optionally(toUnmodifiableList()))
                .map(Album::renumberSequentially)
                .get();
    }

    private static @NonNull List<ExternalAudio> renumberSequentially(@NonNull List<ExternalAudio> audios) {
        final var displayOrder = new AtomicInteger(1);
        return audios.stream()
                .map(audio -> audio.changeDisplayOrder(displayOrder.getAndIncrement()))
                .toList();
    }

    /**
     * 外部音源リストを表示順でソートして取得
     *
     * @return 表示順にソートされた外部音源リスト
     */
    public @NonNull List<ExternalAudio> getExternalAudiosSortedByDisplayOrder() {
        return externalAudios.stream().sorted(Comparator.comparing(ExternalAudio::displayOrder)).toList();
    }

    /**
     * 特定の外部音源を取得
     *
     * @param externalAudioId
     *            外部音源ID
     * @return 外部音源
     */
    public @NonNull ExternalAudio getExternalAudio(ExternalAudio.@NonNull Id externalAudioId) {
        return externalAudios.stream().filter(a -> a.hasId(externalAudioId)).findFirst()
                .orElseThrow(
                        () -> new BusinessRuleViolationException(
                                "External audio with ID " + externalAudioId.value() + " not found"));
    }

    /**
     * 外部音源リストを取得（不変）
     *
     * @return 外部音源リストの不変コピー
     */
    public @NonNull List<ExternalAudio> getExternalAudios() {
        return Collections.unmodifiableList(externalAudios);
    }

    /**
     * 外部音源追加の結果
     *
     * @param album
     *            追加後のアルバム
     * @param externalAudio
     *            追加された外部音源
     */
    public record ExternalAudioAddition(@NonNull Album album, @NonNull ExternalAudio externalAudio) {
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
                                    "Album ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult("value", "Album ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")));
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

        /**
         * 外部入力（文字列）からAlbum.Idを生成します。
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
