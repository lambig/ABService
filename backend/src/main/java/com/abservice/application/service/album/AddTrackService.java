package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.domain.service.TrackAdditionService;
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
 * {@link Album#addTrack(Track)}
 * を呼び出すユースケースです。値検証・組み立ては{@link TrackAdditionService}
 * （アルバムと同時にトラックを登録する{@code RegisterAlbumWithTracksService}とも共有するドメインサービス）に
 * 委譲します。トラック番号の重複はAlbum集約自身が検証します
 * （{@link com.abservice.domain.exception.BusinessRuleViolationException}、409）。
 * </p>
 *
 * <p>
 * {@link BusinessDate} は文字列からの直接生成を提供しないため（パース方式の解釈は境界層の責務）、録音日の
 * ISO-8601文字列の解釈は本サービスが担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AddTrackService implements CommandService<AddTrackInput, AddTrackOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumAccessService albumAccessService;
    private final TrackAdditionService trackAdditionService;

    @WithTransaction
    @Override
    public Uni<AddTrackOutput> execute(AddTrackInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(albumAccessService::findExistingAndClaimEdit)
                .flatMap(
                        album -> trackAdditionService.addTrack(album, toTrackFields(input))
                                .flatMap(
                                        addition -> albumRepository.save(addition.album())
                                                .map(saved -> toOutput(saved, addition.track()))));
    }

    private static TrackAdditionService.TrackFields toTrackFields(AddTrackInput input) {
        return new TrackAdditionService.TrackFields(
                input.trackNo(),
                input.title(),
                input.artistDisplayName(),
                input.artistSortKey(),
                resolveRecordingDate(input.recordingDate()),
                input.recordingPlace(),
                input.isLive());
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
