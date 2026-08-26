package com.abservice.domain.service;

import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.AssetKey;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.lib.Result;
import io.smallrye.mutiny.Uni;
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
     * @return 検証・生成されたAlbum。検証失敗時は{@link ValidationException}で失敗する
     */
    @DomainFactory
    public Uni<Album> create(
            @Nullable String title,
            Result<BusinessDate> releaseDate,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            @Nullable String catalogNumber,
            @Nullable String isdn,
            @Nullable String coverImageKey,
            @Nullable String description,
            @Nullable String descriptionFormat,
            @Nullable EventFields event) {
        return Uni.createFrom()
                .item(
                        () -> validate(
                                title,
                                releaseDate,
                                artistDisplayName,
                                artistSortKey,
                                catalogNumber,
                                isdn,
                                coverImageKey,
                                description,
                                descriptionFormat,
                                event)
                                .resolve(ValidationException::new));
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
            @Nullable EventFields event) {
        return Result.zip(
                Result.zip(
                        AlbumTitle.fromInput(title),
                        releaseDate,
                        ArtistCredit.fromInput(artistDisplayName, artistSortKey),
                        TitleDateArtist::new),
                Result.zip(
                        resolveOptional(CatalogNumber::fromInput, catalogNumber),
                        resolveOptional(Isdn::fromInput, isdn),
                        resolveEvent(event),
                        OptionalFields::new),
                Result.zip(
                        resolveOptional(AssetKey::fromInput, coverImageKey),
                        resolveDescription(description, descriptionFormat),
                        CoverAndDescription::new),
                (base, optional, extra) -> Album.create(
                        base.title(),
                        base.releaseDate(),
                        base.artistCredit(),
                        extra.description(),
                        optional.event().orElse(null),
                        optional.catalogNumber().orElse(null),
                        optional.isdn().orElse(null),
                        extra.coverImageKey().orElse(null)));
    }

    /** 説明なし（blank 入力）を表す検証結果。完全に使い回せる定数。 */
    private static final Result<MarkupContent> EMPTY_DESCRIPTION = Result.success(MarkupContent.EMPTY);

    private static Result<MarkupContent> resolveDescription(@Nullable String content, @Nullable String format) {
        return Optional.ofNullable(content)
                .filter(StringUtils::isNotBlank)
                .map(c -> MarkupContent.fromInput(c, format))
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

    private record CoverAndDescription(Optional<AssetKey> coverImageKey, MarkupContent description) {
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
                                event.note()))
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
