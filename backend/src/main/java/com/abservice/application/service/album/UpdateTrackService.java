package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * トラック更新コマンドサービス
 *
 * <p>
 * {@link Album#updateTrack(Track)} を呼び出すユースケースです。既存トラックの{@code tunes}
 * （チューン構成、#120の対象）はPUT風の全項目置換対象外のため、既存の値をそのまま維持します。トラック番号の重複は
 * Album集約自身が検証します（{@link com.abservice.domain.exception.BusinessRuleViolationException}、409）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class UpdateTrackService implements CommandService<UpdateTrackInput, UpdateTrackOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumAccessService albumAccessService;

    @WithTransaction
    @Override
    public Uni<UpdateTrackOutput> execute(UpdateTrackInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(albumAccessService::findExistingAndClaimEdit)
                .flatMap(
                        album -> Uni.createFrom()
                                .item(
                                        () -> Track.Id.fromInput(input.trackId())
                                                .resolve(ValidationException::new))
                                .map(album::getTrack)
                                .flatMap(
                                        existing -> Uni.createFrom()
                                                .item(
                                                        () -> validate(input, existing)
                                                                .resolve(ValidationException::new))
                                                .map(album::updateTrack))
                                .flatMap(albumRepository::save)
                                .map(saved -> toOutput(saved, Track.Id.of(Objects.requireNonNull(input.trackId())))));
    }

    static Result<Track> validate(UpdateTrackInput input, Track existing) {
        return Result.zip(
                resolveArtistCredit(input.artistDisplayName(), input.artistSortKey()),
                resolveRecordingDate(input.recordingDate()),
                OptionalFields::new)
                .flatMap(
                        optional -> Result.zip(
                                trackNoPolicy().verify(input.trackNo(), Function.identity()),
                                TrackTitle.fromInput(input.title()),
                                (trackNo, title) -> Track.reconstruct(
                                        existing.id(),
                                        trackNo,
                                        title,
                                        optional.artistCredit().orElse(null),
                                        optional.recordingDate().orElse(null),
                                        input.recordingPlace(),
                                        input.isLive(),
                                        existing.getTunes())));
    }

    private static Policy<Integer> trackNoPolicy() {
        return Policy.of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "trackNo",
                        "Track number is required",
                        "TRACK_NO_REQUIRED"));
    }

    private record OptionalFields(Optional<ArtistCredit> artistCredit, Optional<BusinessDate> recordingDate) {
    }

    private static Result<Optional<ArtistCredit>> resolveArtistCredit(
            @Nullable String displayName,
            @Nullable String sortKey) {
        return Optional.ofNullable(displayName)
                .filter(StringUtils::isNotBlank)
                .map(
                        name -> ArtistCredit.fromInput(name, sortKey)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<ArtistCredit>>success(Optional.empty()));
    }

    private static Result<Optional<BusinessDate>> resolveRecordingDate(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> parseDate(v)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<BusinessDate>>success(Optional.empty()));
    }

    private static Result<BusinessDate> parseDate(String value) {
        try {
            return Result.success(BusinessDate.of(LocalDate.parse(value)));
        } catch (DateTimeParseException e) {
            return Result.failure(
                    new ErrorResult(
                            "recordingDate",
                            "日付の形式が不正です: " + value,
                            "TRACK_RECORDING_DATE_INVALID"));
        }
    }

    private static UpdateTrackOutput toOutput(Album album, Track.Id trackId) {
        final var track = album.getTrack(trackId);
        return new UpdateTrackOutput(
                album.id().value(),
                track.id().value(),
                track.trackNo(),
                track.title().value());
    }
}
