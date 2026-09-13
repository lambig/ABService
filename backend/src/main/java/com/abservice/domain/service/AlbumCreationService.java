package com.abservice.domain.service;

import com.abservice.domain.model.DomainFactory;
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
import com.abservice.lib.Result;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバムの新規登録（検証・組み立て）を担うドメインサービス
 *
 * <p>
 * 外部入力の生の値からAlbumを検証・組み立てるロジックを提供します。単体の{@link com.abservice.application.service.album.CreateAlbumService}
 * だけでなく、トラックを同時登録するユースケースからも呼ばれる（{@code
 * com.abservice.application.service.album.RegisterAlbumWithTracksService}）ため、
 * 特定のCommandServiceに属さないドメインサービスとして切り出しています。
 * </p>
 *
 * <p>
 * {@link BusinessDate} 文字列の解釈（ISO-8601パース）はドメイン層が {@code java.time} に直接依存しないための
 * 境界層の責務のため、{@code releaseDate}・イベント開催日は呼び出し側で解決済みの {@link Result} を渡してください
 * （{@code Result} は成功時は解決済みの値、失敗時はパース失敗等のエラーを保持し、他の検証項目と合わせて集約されます）。
 * </p>
 *
 * <p>
 * 検証エラーは {@link Result} で返します。例外へ変えるのは呼び出し元（application 層）の判断で、そこは入力を
 * 組み立てた場所として、エラーの位置を自分の入力パスへ写せる立場でもあります（DECISIONS 29）。エラーの位置は
 * 本サービスの引数の綴り（{@code title} / {@code artistDisplayName} / {@code catalogNumber}
 * / {@code isdn} / {@code coverImageKey} / {@code description} /
 * {@code descriptionFormat} /
 * {@code event.name}）で返します。値オブジェクトはどの引数から渡されたかを知らないため、写せるのはここだけです （同じ
 * {@code value} を返す {@code AlbumTitle} と {@code CatalogNumber} と {@code Isdn}
 * を、 呼び出し元では区別できません）。
 * </p>
 */
@ApplicationScoped
public class AlbumCreationService implements DomainService {

    /**
     * 外部入力からアルバムを検証・生成する
     *
     * @param title
     *            アルバムタイトル
     * @param releaseDate
     *            リリース日（呼び出し側で文字列から解決済みのResult）
     * @param artistDisplayName
     *            アーティスト表示名
     * @param artistSortKey
     *            アーティストソートキー（nullable）
     * @param catalogNumber
     *            カタログナンバー（nullable）
     * @param isdn
     *            ISDN（nullable）
     * @param coverImageKey
     *            カバー画像のアセットキー（nullable。アップロード基盤が返す{@code assetKey}）
     * @param description
     *            概要説明（nullable。空白のみは説明なしとして扱う）
     * @param descriptionFormat
     *            概要説明のマークアップ形式（{@code description}を指定する場合のみ必須）
     * @param event
     *            初出イベント情報（nullable）
     * @param basePrice
     *            頒布の基準額（nullable。null は額が決まっていない）
     * @param originalWorkNote
     *            原作の出典の記述（nullable。空白のみは記述なしとして扱う）
     * @return 成功時は検証・生成されたAlbum、失敗時はエラー
     */
    @DomainFactory
    public Result<Album> create(
            @Nullable String title,
            Result<BusinessDate> releaseDate,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            @Nullable String catalogNumber,
            @Nullable String isdn,
            @Nullable String coverImageKey,
            @Nullable String description,
            @Nullable String descriptionFormat,
            @Nullable EventFields event,
            @Nullable BasePriceFields basePrice,
            @Nullable String originalWorkNote) {
        return validate(
                title,
                releaseDate,
                artistDisplayName,
                artistSortKey,
                catalogNumber,
                isdn,
                coverImageKey,
                description,
                descriptionFormat,
                event,
                basePrice,
                originalWorkNote);
    }

    @DomainFactory
    static Result<Album> validate(
            @Nullable String title,
            Result<BusinessDate> releaseDate,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            @Nullable String catalogNumber,
            @Nullable String isdn,
            @Nullable String coverImageKey,
            @Nullable String description,
            @Nullable String descriptionFormat,
            @Nullable EventFields event,
            @Nullable BasePriceFields basePrice,
            @Nullable String originalWorkNote) {
        return Result.zip(
                Result.zip(
                        AlbumTitle.fromInput(title)
                                .withErrorField("title"),
                        releaseDate,
                        ArtistCredit.fromInput(artistDisplayName, artistSortKey)
                                .withErrorField("artistDisplayName"),
                        TitleDateArtist::new),
                Result.zip(
                        resolveOptional(CatalogNumber::fromInput, catalogNumber)
                                .withErrorField("catalogNumber"),
                        resolveOptional(Isdn::fromInput, isdn)
                                .withErrorField("isdn"),
                        resolveEvent(event),
                        OptionalFields::new),
                Result.zip(
                        resolveOptional(AssetKey::fromInput, coverImageKey)
                                .withErrorField("coverImageKey"),
                        resolveDescription(description, descriptionFormat),
                        resolveBasePrice(basePrice),
                        resolveOptional(OriginalWorkNote::fromInput, originalWorkNote)
                                .withErrorField("originalWorkNote"),
                        Extras::new),
                (base, optional, extra) -> Album.create(
                        base.title(),
                        base.releaseDate(),
                        base.artistCredit(),
                        extra.description(),
                        optional.event().orElse(null),
                        optional.catalogNumber().orElse(null),
                        optional.isdn().orElse(null),
                        extra.coverImageKey().orElse(null),
                        extra.basePrice().orElse(null),
                        extra.originalWorkNote().orElse(null)));
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

    /**
     * 初出イベント情報の入力
     *
     * <p>
     * {@code date} は呼び出し側で文字列から解決済みの{@link Result}を渡してください
     * （{@link #create}の{@code releaseDate}と同じ理由）。
     * </p>
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日（呼び出し側で文字列から解決済みのResult。未指定時は{@code Result.success(Optional.empty())}）
     * @param place
     *            会場（nullable）
     * @param spaceNumber
     *            スペース番号（nullable）
     * @param note
     *            補足情報（nullable）
     */
    public record EventFields(
            @Nullable String name,
            Result<Optional<BusinessDate>> date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String note) {
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

    /**
     * 頒布の基準額の入力
     *
     * <p>
     * 経路・担い手・地域ごとの額は作品が持ちません（発表の側が持つ。#201）。ここで受け取るのは、 そこから上書きされる基準の額だけです。
     * </p>
     *
     * @param amount
     *            金額（基準額を指定する場合は必須）
     * @param currency
     *            通貨コード（nullable。未指定は円）
     */
    public record BasePriceFields(
            @Nullable Integer amount,
            @Nullable String currency) {
    }

    private static Result<Optional<Price>> resolveBasePrice(@Nullable BasePriceFields basePrice) {
        return Optional.ofNullable(basePrice)
                .map(AlbumCreationService::validateBasePrice)
                .orElseGet(() -> Result.<Optional<Price>>success(Optional.empty()));
    }

    private static Result<Optional<Price>> validateBasePrice(BasePriceFields basePrice) {
        return Price.fromInput(basePrice.amount(), basePrice.currency())
                .mapErrorFields(field -> "basePrice." + field)
                .map(Optional::of);
    }

    private static Result<Optional<EventReleasedAt>> resolveEvent(@Nullable EventFields event) {
        return Optional.ofNullable(event)
                .map(AlbumCreationService::validateEvent)
                .orElseGet(() -> Result.<Optional<EventReleasedAt>>success(Optional.empty()));
    }

    private static Result<Optional<EventReleasedAt>> validateEvent(EventFields event) {
        return event.date()
                .flatMap(
                        date -> EventReleasedAt.fromInput(
                                event.name(),
                                date.orElse(null),
                                event.place(),
                                event.spaceNumber(),
                                event.note())
                                .withErrorField("event.name"))
                .map(Optional::of);
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
}
