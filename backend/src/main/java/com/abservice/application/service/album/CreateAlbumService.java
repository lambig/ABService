package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバム作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateAlbumInput}）から新規 {@link Album} を生成して永続化するユースケースです。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して {@code Album} を組み立てるオーケストレーションに徹します。検証失敗は
 * {@link ValidationException} に集約し、HTTP への変換は presentation 層の ExceptionMapper
 * が担います。
 * </p>
 *
 * <p>
 * {@link BusinessDate} は文字列からの直接生成を提供しないため（パース方式の解釈は境界層の責務）、リリース日・ 初出イベント開催日の
 * ISO-8601文字列の解釈は本サービスが担います。
 * </p>
 */
@ApplicationScoped
public class CreateAlbumService implements CommandService<CreateAlbumInput, CreateAlbumOutput> {

    private final AlbumRepository albumRepository;

    /**
     * @param albumRepository
     *            アルバムリポジトリ
     */
    public CreateAlbumService(AlbumRepository albumRepository) {
        this.albumRepository = albumRepository;
    }

    @WithTransaction
    @Override
    public Uni<CreateAlbumOutput> execute(CreateAlbumInput input) {
        return Uni.createFrom()
                .item(
                        () -> validate(input)
                                .resolve(ValidationException::new))
                .flatMap(albumRepository::save)
                .map(CreateAlbumService::toOutput);
    }

    static Result<Album> validate(CreateAlbumInput input) {
        return Result.zip(
                AlbumTitle.fromInput(input.title()),
                resolveReleaseDate(input.releaseDate()),
                ArtistCredit.fromInput(input.artistDisplayName(), input.artistSortKey()),
                TitleDateArtist::new)
                .flatMap(
                        base -> resolveOptional(CatalogNumber::fromInput, input.catalogNumber())
                                .flatMap(
                                        catalogNumber -> resolveOptional(Isdn::fromInput, input.isdn())
                                                .flatMap(
                                                        isdn -> resolveEvent(input.event())
                                                                .map(
                                                                        event -> Album.create(
                                                                                base.title(),
                                                                                base.releaseDate(),
                                                                                base.artistCredit(),
                                                                                event.orElse(null),
                                                                                catalogNumber.orElse(null),
                                                                                isdn.orElse(null))))));
    }

    private record TitleDateArtist(AlbumTitle title, BusinessDate releaseDate, ArtistCredit artistCredit) {
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

    private static Result<Optional<EventReleasedAt>> resolveEvent(
            CreateAlbumInput.@Nullable EventInput input) {
        return Optional.ofNullable(input)
                .map(CreateAlbumService::validateEvent)
                .orElseGet(() -> Result.<Optional<EventReleasedAt>>success(Optional.empty()));
    }

    private static Result<Optional<EventReleasedAt>> validateEvent(CreateAlbumInput.EventInput input) {
        return resolveEventDate(input.date())
                .flatMap(
                        date -> EventReleasedAt.fromInput(
                                input.name(),
                                date.orElse(null),
                                input.place(),
                                input.spaceNumber(),
                                input.note()))
                .map(Optional::of);
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

    private static <T> Result<Optional<T>> resolveOptional(
            Function<String, Result<T>> fromInput,
            @Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> fromInput.apply(v)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<T>>success(Optional.empty()));
    }

    private static CreateAlbumOutput toOutput(Album album) {
        return new CreateAlbumOutput(
                album.id().value(),
                album.title().value(),
                album.releaseDate().asLocalDate().toString(),
                album.artistCredit().displayName().value());
    }
}
