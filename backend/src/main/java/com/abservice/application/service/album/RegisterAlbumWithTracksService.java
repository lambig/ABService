package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumCreationService;
import com.abservice.domain.service.TrackAdditionService;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバムとその初期トラック一覧をワンリクエストで登録するコマンドサービス
 *
 * <p>
 * 業務上は「アルバムを登録する」「トラックを追加する」という2段階の操作だが、1リクエストで完結させたいユースケース向けに、
 * {@link AlbumCreationService}と{@link TrackAdditionService}（いずれも単体の{@link CreateAlbumService}・
 * {@link AddTrackService}とも共有するドメインサービス）を順に呼び出して1つのAlbumを組み立て、1回だけ永続化します。
 * トラックは入力リストの順に1件ずつ追加するため、途中のトラックが検証エラー・トラック番号重複の場合はそこで失敗し、
 * 後続のトラックは追加されません（部分的な成功はありません。トランザクション全体がロールバックされます）。
 * </p>
 *
 * <p>
 * {@link BusinessDate} は文字列からの直接生成を提供しないため（パース方式の解釈は境界層の責務）、リリース日・
 * 初出イベント開催日・各トラックの録音日のISO-8601文字列の解釈は本サービスが担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class RegisterAlbumWithTracksService
        implements
            CommandService<RegisterAlbumWithTracksInput, RegisterAlbumWithTracksOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumCreationService albumCreationService;
    private final TrackAdditionService trackAdditionService;

    @WithTransaction
    @Override
    public Uni<RegisterAlbumWithTracksOutput> execute(RegisterAlbumWithTracksInput input) {
        return albumCreationService.create(
                input.title(),
                resolveReleaseDate(input.releaseDate()),
                input.artistDisplayName(),
                input.artistSortKey(),
                input.catalogNumber(),
                input.isdn(),
                toEventFields(input.event()))
                .flatMap(album -> addTracks(album, tracksOf(input)))
                .flatMap(albumRepository::save)
                .map(RegisterAlbumWithTracksService::toOutput);
    }

    private static List<RegisterAlbumWithTracksInput.TrackInput> tracksOf(
            RegisterAlbumWithTracksInput input) {
        return Optional.ofNullable(input.tracks())
                .orElseGet(List::of);
    }

    private Uni<Album> addTracks(Album album, List<RegisterAlbumWithTracksInput.TrackInput> tracks) {
        return tracks.stream()
                .reduce(
                        Uni.createFrom().item(album),
                        (accUni, track) -> accUni.flatMap(a -> addOneTrack(a, track)),
                        (a, b) -> {
                            throw new UnsupportedOperationException();
                        });
    }

    private Uni<Album> addOneTrack(Album album, RegisterAlbumWithTracksInput.@Nullable TrackInput track) {
        return Optional.ofNullable(track)
                .map(RegisterAlbumWithTracksService::toTrackFields)
                .map(
                        fields -> trackAdditionService.addTrack(album, fields)
                                .map(TrackAdditionService.Addition::album))
                .orElseGet(
                        () -> Uni.createFrom()
                                .failure(
                                        new ValidationException(
                                                List.of(
                                                        new ErrorResult(
                                                                "tracks",
                                                                "トラック情報は必須です",
                                                                "TRACK_REQUIRED")))));
    }

    private static TrackAdditionService.TrackFields toTrackFields(RegisterAlbumWithTracksInput.TrackInput t) {
        return new TrackAdditionService.TrackFields(
                t.trackNo(),
                t.title(),
                t.artistDisplayName(),
                t.artistSortKey(),
                resolveOptionalDate(
                        t.recordingDate(),
                        "recordingDate",
                        "TRACK_RECORDING_DATE_INVALID"),
                t.recordingPlace(),
                t.isLive());
    }

    private static AlbumCreationService.@Nullable EventFields toEventFields(
            RegisterAlbumWithTracksInput.@Nullable EventInput event) {
        return Optional.ofNullable(event)
                .map(
                        e -> new AlbumCreationService.EventFields(
                                e.name(),
                                resolveOptionalDate(
                                        e.date(),
                                        "event.date",
                                        "ALBUM_EVENT_DATE_INVALID"),
                                e.place(),
                                e.spaceNumber(),
                                e.note()))
                .orElse(null);
    }

    private static Result<BusinessDate> resolveReleaseDate(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> parseDate(
                                v,
                                "releaseDate",
                                "ALBUM_RELEASE_DATE_INVALID"))
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "releaseDate",
                                        "リリース日は必須です",
                                        "ALBUM_RELEASE_DATE_REQUIRED")));
    }

    private static Result<Optional<BusinessDate>> resolveOptionalDate(
            @Nullable String value,
            String field,
            String invalidErrorCode) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> parseDate(
                                v,
                                field,
                                invalidErrorCode)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<BusinessDate>>success(Optional.empty()));
    }

    private static Result<BusinessDate> parseDate(
            String value,
            String field,
            String invalidErrorCode) {
        try {
            return Result.success(BusinessDate.of(LocalDate.parse(value)));
        } catch (DateTimeParseException e) {
            return Result.failure(
                    new ErrorResult(
                            field,
                            "日付の形式が不正です: " + value,
                            invalidErrorCode));
        }
    }

    private static RegisterAlbumWithTracksOutput toOutput(Album album) {
        return new RegisterAlbumWithTracksOutput(
                album.id().value(),
                album.title().value(),
                album.releaseDate().asLocalDate().toString(),
                album.artistCredit().displayName().value(),
                album.getTracksSortedByTrackNo().stream()
                        .map(
                                track -> new RegisterAlbumWithTracksOutput.TrackSummary(
                                        track.id().value(),
                                        track.trackNo(),
                                        track.title().value()))
                        .toList());
    }
}
