package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumCreationService;
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
 * アルバム作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateAlbumInput}）から新規 {@link Album} を生成して永続化するユースケースです。
 * 値検証・組み立ては{@link AlbumCreationService}（トラックを同時登録する
 * {@code RegisterAlbumWithTracksService}とも共有するドメインサービス）に委譲します。
 * </p>
 *
 * <p>
 * {@link BusinessDate} は文字列からの直接生成を提供しないため（パース方式の解釈は境界層の責務）、リリース日・ 初出イベント開催日の
 * ISO-8601文字列の解釈は本サービスが担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class CreateAlbumService implements CommandService<CreateAlbumInput, CreateAlbumOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumCreationService albumCreationService;

    @WithTransaction
    @Override
    public Uni<CreateAlbumOutput> execute(CreateAlbumInput input) {
        return albumCreationService.create(
                input.title(),
                resolveReleaseDate(input.releaseDate()),
                input.artistDisplayName(),
                input.artistSortKey(),
                input.catalogNumber(),
                input.isdn(),
                toEventFields(input.event()))
                .flatMap(albumRepository::save)
                .map(CreateAlbumService::toOutput);
    }

    private static AlbumCreationService.@Nullable EventFields toEventFields(
            CreateAlbumInput.@Nullable EventInput event) {
        return Optional.ofNullable(event)
                .map(
                        e -> new AlbumCreationService.EventFields(
                                e.name(),
                                resolveEventDate(e.date()),
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

    private static Result<Optional<BusinessDate>> resolveEventDate(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> parseDate(
                                v,
                                "event.date",
                                "ALBUM_EVENT_DATE_INVALID")
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

    private static CreateAlbumOutput toOutput(Album album) {
        return new CreateAlbumOutput(
                album.id().value(),
                album.title().value(),
                album.releaseDate().asLocalDate().toString(),
                album.artistCredit().displayName().value());
    }
}
