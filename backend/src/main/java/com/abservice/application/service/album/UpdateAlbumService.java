package com.abservice.application.service.album;

import com.abservice.application.exception.ConflictingEditException;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.OriginalWorkNote;
import com.abservice.domain.model.vo.album.Price;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.AssetKey;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.MarkupContent;
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
 * アルバム更新コマンドサービス
 *
 * <p>
 * 外部入力（{@link UpdateAlbumInput}）から既存 {@link Album} のCreate相当フィールド
 * （title/releaseDate/artistCredit/eventReleasedAt/catalogNumber/isdn）をPUT風に全項目置換する
 * ユースケースです。トラックは対象外のため既存の値をそのまま維持します。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して既存 {@code Album} を更新後の状態へ組み替えるオーケストレーションに徹します。検証失敗は
 * {@link ValidationException} に、対象アルバムの不在は {@link EntityNotFoundException}
 * に集約し、HTTP への変換は presentation 層の ExceptionMapper が担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class UpdateAlbumService implements CommandService<UpdateAlbumInput, UpdateAlbumOutput> {

    private final AlbumRepository albumRepository;

    private final AlbumAccessService albumAccessService;

    @WithTransaction
    @Override
    public Uni<UpdateAlbumOutput> execute(UpdateAlbumInput input) {
        return Uni.createFrom()
                .item(() -> requested(input))
                .flatMap(
                        requested -> albumAccessService
                                .findExistingAndClaimEditWithRevision(requested.albumId()))
                .map(claimed -> claimedAsOf(claimed, input))
                .map(
                        existing -> validateAndApply(existing, input)
                                .resolve(ValidationException::new))
                .flatMap(albumRepository::saveWithRevision)
                .map(UpdateAlbumService::toOutput);
    }

    /** 更新の対象と条件。どちらも欠けていれば検証エラーで、集約を掴む前に決まる */
    private record Requested(Album.Id albumId, int expectedRevision) {
    }

    private static Requested requested(UpdateAlbumInput input) {
        return Result.zip(
                Album.Id.fromInput(input.albumId())
                        .mapErrorFields(field -> "albumId"),
                expectedRevision(input.expectedRevision()),
                Requested::new)
                .resolve(ValidationException::new);
    }

    /**
     * 編集を始めた時点の世代。
     *
     * <p>
     * 未指定を「条件なし」として通さない。全項目置換のため、条件を持たない更新は、編集の間に入った別の保存を
     * 黙って消す（#287）。後勝ちを選ぶ判断はしていないため、指定を要求する。
     * </p>
     */
    private static Result<Integer> expectedRevision(@Nullable Integer value) {
        return Optional.ofNullable(value)
                .map(Result::success)
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "expectedRevision",
                                        "編集を始めた時点の世代は必須です",
                                        "ALBUM_EXPECTED_REVISION_REQUIRED")));
    }

    /**
     * 掴んだ行の世代が、編集を始めた時点と同じであることを確かめる。
     *
     * <p>
     * 違っていれば、この更新が持っている値は既に古い。届いた値を最新へ適用すると、間に入った保存を消すため拒む。
     * </p>
     */
    private static Album claimedAsOf(AlbumRepository.Revisioned claimed, UpdateAlbumInput input) {
        return Objects.equals(claimed.revision().value(), input.expectedRevision())
                ? claimed.album()
                : conflicting(claimed);
    }

    private static Album conflicting(AlbumRepository.Revisioned claimed) {
        throw new ConflictingEditException(
                "アルバム %s は編集を始めた後に更新されています".formatted(claimed.album().id().value()));
    }

    static Result<Album> validateAndApply(Album existing, UpdateAlbumInput input) {
        return Result.zip(
                Result.zip(
                        AlbumTitle.fromInput(input.title())
                                .mapErrorFields(field -> "title"),
                        resolveReleaseDate(input.releaseDate()),
                        ArtistCredit.fromInput(input.artistDisplayName(), input.artistSortKey())
                                .mapErrorFields(field -> "artistDisplayName"),
                        TitleDateArtist::new),
                Result.zip(
                        resolveOptional(CatalogNumber::fromInput, input.catalogNumber())
                                .mapErrorFields(field -> "catalogNumber"),
                        resolveOptional(Isdn::fromInput, input.isdn())
                                .mapErrorFields(field -> "isdn"),
                        resolveEvent(input.event()),
                        OptionalFields::new),
                Result.zip(
                        resolveOptional(AssetKey::fromInput, input.coverImageKey())
                                .mapErrorFields(field -> "coverImageKey"),
                        resolveDescription(input.description(), input.descriptionFormat()),
                        resolveBasePrice(input.basePrice()),
                        resolveOptional(OriginalWorkNote::fromInput, input.originalWorkNote())
                                .mapErrorFields(field -> "originalWorkNote"),
                        Extras::new),
                (base, optional, extra) -> existing.changeTitle(base.title())
                        .changeReleaseDate(base.releaseDate())
                        .changeArtistCredit(base.artistCredit())
                        .changeDescription(extra.description())
                        .changeEventReleasedAt(optional.event().orElse(null))
                        .changeCatalogNumber(optional.catalogNumber().orElse(null))
                        .changeIsdn(optional.isdn().orElse(null))
                        .changeCoverImageKey(extra.coverImageKey().orElse(null))
                        .changeBasePrice(extra.basePrice().orElse(null))
                        .changeOriginalWorkNote(extra.originalWorkNote().orElse(null)));
    }

    private static Result<Optional<Price>> resolveBasePrice(
            UpdateAlbumInput.@Nullable BasePriceInput basePrice) {
        return Optional.ofNullable(basePrice)
                .map(UpdateAlbumService::validateBasePrice)
                .orElseGet(() -> Result.<Optional<Price>>success(Optional.empty()));
    }

    private static Result<Optional<Price>> validateBasePrice(UpdateAlbumInput.BasePriceInput basePrice) {
        return Price.fromInput(basePrice.amount(), basePrice.currency())
                .mapErrorFields(field -> "basePrice." + field)
                .map(Optional::of);
    }

    /** 説明なし（blank 入力）を表す検証結果。完全に使い回せる定数。 */
    private static final Result<MarkupContent> EMPTY_DESCRIPTION = Result.success(MarkupContent.EMPTY);

    private static Result<MarkupContent> resolveDescription(@Nullable String content, @Nullable String format) {
        return Optional.ofNullable(content)
                .filter(StringUtils::isNotBlank)
                .map(
                        c -> MarkupContent.fromInput(c, format)
                                .mapErrorFields(
                                        field -> "format".equals(field)
                                                ? "descriptionFormat"
                                                : "description"))
                .orElse(EMPTY_DESCRIPTION);
    }

    private record TitleDateArtist(AlbumTitle title, BusinessDate releaseDate, ArtistCredit artistCredit) {
    }

    private record OptionalFields(
            Optional<CatalogNumber> catalogNumber,
            Optional<Isdn> isdn,
            Optional<EventReleasedAt> event) {
    }

    private record Extras(
            Optional<AssetKey> coverImageKey,
            MarkupContent description,
            Optional<Price> basePrice,
            Optional<OriginalWorkNote> originalWorkNote) {
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
            UpdateAlbumInput.@Nullable EventInput input) {
        return Optional.ofNullable(input)
                .map(UpdateAlbumService::validateEvent)
                .orElseGet(() -> Result.<Optional<EventReleasedAt>>success(Optional.empty()));
    }

    private static Result<Optional<EventReleasedAt>> validateEvent(UpdateAlbumInput.EventInput input) {
        return resolveEventDate(input.date())
                .flatMap(
                        date -> EventReleasedAt.fromInput(
                                input.name(),
                                date.orElse(null),
                                input.place(),
                                input.spaceNumber(),
                                input.note()).mapErrorFields(field -> "event.name"))
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

    private static UpdateAlbumOutput toOutput(AlbumRepository.Revisioned saved) {
        return new UpdateAlbumOutput(
                saved.album().id().value(),
                saved.revision().value(),
                saved.album().title().value(),
                saved.album().releaseDate().asLocalDate().toString(),
                saved.album().artistCredit().displayName().value());
    }
}
