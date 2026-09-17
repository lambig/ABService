package com.abservice.application.service.album;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumCreationService;
import com.abservice.domain.service.ExternalAudioAssemblyService;
import com.abservice.domain.service.TrackAssemblyService;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

/**
 * アルバムとその初期トラック一覧をワンリクエストで登録するコマンドサービス
 *
 * <p>
 * 作品の子は作品の外に存在できないため、書く経路は集約ルートに1つだけ置きます（#391）。本サービスは
 * {@link AlbumCreationService}・{@link TrackAssemblyService}・{@link ExternalAudioAssemblyService}
 * を呼んで 1つのAlbumを組み立て、1回だけ永続化します。曲目も外部音源も**並びごと**受け取り、届いた配列がそのまま順になります。
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
    private final TrackAssemblyService trackAssemblyService;
    private final ExternalAudioAssemblyService externalAudioAssemblyService;

    /** チューン名を繋ぐ区切り（#360）。応答はトラックの名を返すため、組み立てにここでも要る */
    @ConfigProperty(name = "abservice.track.tune-title-separator")
    private final String tuneTitleSeparator;

    @WithTransaction
    @Override
    public Uni<RegisterAlbumWithTracksOutput> execute(RegisterAlbumWithTracksInput input) {
        return Uni.createFrom()
                .item(() -> registered(input))
                .flatMap(albumRepository::save)
                .map(saved -> toOutput(saved, tuneTitleSeparator));
    }

    /**
     * 作品と、その曲目・外部音源を組み立てる。
     *
     * <p>
     * 本体・曲目・音源の検証は1つの結果へまとめる。曲目だけが誤っている場合も本体の誤りと並べて返すため、要求元は
     * 1往復ですべての位置を受け取る。エラーの位置（{@code tracks[i].<項目>}）は、一覧を受け取るドメインサービスが
     * 冠する（DECISIONS 29）。
     * </p>
     */
    private Album registered(RegisterAlbumWithTracksInput input) {
        return Result.zip(
                albumCreationService.create(
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
                        toBasePriceFields(input.basePrice()),
                        input.originalWorkNote()),
                trackAssemblyService.resolveTracks(TrackInput.toFields(input.tracks())),
                externalAudioAssemblyService.resolveExternalAudios(
                        ExternalAudioInput.toFields(input.externalAudios())),
                (album, tracks, audios) -> album.replaceTracks(tracks)
                        .replaceExternalAudios(audios))
                .resolve(ValidationException::new);
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
                                e.circleName(),
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

    /* 応答が返すのは入力の写しではなくトラックの名（#360）。タイトルを省いたトラックはチューン名で名乗る */
    private static RegisterAlbumWithTracksOutput toOutput(Album album, String tuneTitleSeparator) {
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
                                        track.name(tuneTitleSeparator).value()))
                        .toList());
    }
}
