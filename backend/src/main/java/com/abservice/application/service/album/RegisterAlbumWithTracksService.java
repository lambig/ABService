package com.abservice.application.service.album;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
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
import java.util.stream.IntStream;
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
 * 初出イベント開催日のISO-8601文字列の解釈は本サービスが担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.CONFLICT})
public class RegisterAlbumWithTracksService
        implements
            CommandService<RegisterAlbumWithTracksInput, RegisterAlbumWithTracksOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumCreationService albumCreationService;
    private final TrackAdditionService trackAdditionService;

    @WithTransaction
    @Override
    public Uni<RegisterAlbumWithTracksOutput> execute(RegisterAlbumWithTracksInput input) {
        return Uni.createFrom()
                .item(
                        () -> albumCreationService.create(
                                input.title(),
                                resolveReleaseDate(input.releaseDate()),
                                input.artistDisplayName(),
                                input.artistSortKey(),
                                input.catalogNumber(),
                                input.isdn(),
                                input.coverImageKey(),
                                input.description(),
                                input.descriptionFormat(),
                                toEventFields(input.event()),
                                toBasePriceFields(input.basePrice()))
                                .resolve(ValidationException::new))
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
        return IntStream.range(0, tracks.size())
                .boxed()
                .reduce(
                        Uni.createFrom().item(album),
                        (accUni, index) -> accUni.flatMap(
                                a -> addOneTrack(
                                        a,
                                        tracks.get(index),
                                        index)),
                        (a, b) -> {
                            throw new UnsupportedOperationException();
                        });
    }

    /**
     * トラック1件を追加する。エラーの位置は、そのトラックの入力パス（{@code tracks[i].<項目>}）へ写す。
     *
     * <p>
     * ドメインサービスが返すのは自分の入力（{@code TrackFields}）の綴りで、自分が何番目のトラックかは知らない。
     * 添字を知っているのは一覧を組み立てるここだけで、落とすとどのトラックが不正なのかを呼び出し元が特定できない。
     * </p>
     */
    private Uni<Album> addOneTrack(
            Album album,
            RegisterAlbumWithTracksInput.@Nullable TrackInput track,
            int index) {
        return Uni.createFrom()
                .item(
                        () -> Optional.ofNullable(track)
                                .map(RegisterAlbumWithTracksService::toTrackFields)
                                .map(
                                        fields -> trackAdditionService.addTrack(album, fields)
                                                .mapErrorFields(field -> "tracks[" + index + "]." + field))
                                .orElseGet(() -> Result.<TrackAdditionService.Addition>failure(missingTrack(index)))
                                .resolve(ValidationException::new))
                .map(TrackAdditionService.Addition::album);
    }

    /** 行そのものが無い場合は、その要素の位置を指す（項目のパスを持たないため添字までで止める）。 */
    private static ErrorResult missingTrack(int index) {
        return new ErrorResult(
                "tracks[" + index + "]",
                "トラック情報は必須です",
                "TRACK_REQUIRED");
    }

    private static TrackAdditionService.TrackFields toTrackFields(RegisterAlbumWithTracksInput.TrackInput t) {
        return new TrackAdditionService.TrackFields(
                t.trackNo(),
                t.title(),
                t.artistDisplayName(),
                t.artistSortKey(),
                TrackTuneInput.toFields(t.tunes()));
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

    private static AlbumCreationService.@Nullable BasePriceFields toBasePriceFields(
            RegisterAlbumWithTracksInput.@Nullable BasePriceInput basePrice) {
        return Optional.ofNullable(basePrice)
                .map(
                        p -> new AlbumCreationService.BasePriceFields(
                                p.amount(),
                                p.currency()))
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
