package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumExistenceService;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * トラック追加コマンドサービス
 *
 * <p>
 * {@link Album#addTrack(Track)} を呼び出すユースケースです。トラック番号の重複はAlbum集約自身が検証します
 * （{@link com.abservice.domain.exception.BusinessRuleViolationException}、409）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AddTrackService implements CommandService<AddTrackInput, AddTrackOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumExistenceService albumExistenceService;

    @WithTransaction
    @Override
    public Uni<AddTrackOutput> execute(AddTrackInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(albumExistenceService::findExisting)
                .flatMap(
                        album -> Uni.createFrom()
                                .item(
                                        () -> validate(input)
                                                .resolve(ValidationException::new))
                                .flatMap(
                                        track -> albumRepository.save(album.addTrack(track))
                                                .map(saved -> toOutput(saved, track))));
    }

    static Result<Track> validate(AddTrackInput input) {
        return Result.zip(
                resolveArtistCredit(input.artistDisplayName(), input.artistSortKey()),
                resolveRecordingDate(input.recordingDate()),
                OptionalFields::new)
                .flatMap(
                        optional -> Track.fromInput(
                                input.trackNo(),
                                input.title(),
                                optional.artistCredit().orElse(null),
                                optional.recordingDate().orElse(null),
                                input.recordingPlace(),
                                input.isLive()));
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

    private static AddTrackOutput toOutput(Album album, Track track) {
        return new AddTrackOutput(
                album.id().value(),
                track.id().value(),
                track.trackNo(),
                track.title().value());
    }
}
