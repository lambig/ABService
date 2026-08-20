package com.abservice.application.service.albumarticle;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.albumarticle.CreateAlbumArticleInput.DistributionInput;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.aggregate.albumarticle.AlbumDistribution;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.repository.albumarticle.AlbumArticleRepository;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateAlbumArticleInput}）から新規 {@link AlbumArticle}
 * を生成して永続化するユースケースです。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して {@code AlbumArticle} を組み立てるオーケストレーションに徹します。検証失敗は
 * {@link ValidationException} に集約し、HTTP への変換は presentation 層の ExceptionMapper
 * が担います。
 * </p>
 *
 * <p>
 * 本サービスは {@code Album.Id.of} で不正な入力を捕捉して {@code Result} へ変換する境界層を担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class CreateAlbumArticleService implements CommandService<CreateAlbumArticleInput, CreateAlbumArticleOutput> {

    private final AlbumArticleRepository albumArticleRepository;

    @WithTransaction
    @Override
    public Uni<CreateAlbumArticleOutput> execute(CreateAlbumArticleInput input) {
        return Uni.createFrom()
                .item(
                        () -> validate(input)
                                .resolve(ValidationException::new))
                .flatMap(albumArticleRepository::save)
                .map(CreateAlbumArticleService::toOutput);
    }

    static Result<AlbumArticle> validate(CreateAlbumArticleInput input) {
        return Result.zip(
                resolveAlbumId(input.albumId()),
                resolveLabelTag(input.labelTag()),
                resolveDistribution(input.distribution()),
                (albumId, labelTag, distribution) -> AlbumArticle.create(
                        albumId,
                        input.introLong(),
                        input.introShort(),
                        input.firstEventSpace(),
                        labelTag.orElse(null),
                        distribution.orElse(null)));
    }

    private static Result<Album.Id> resolveAlbumId(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(CreateAlbumArticleService::parseAlbumId)
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "albumId",
                                        "アルバムIDは必須です",
                                        "ALBUM_ID_REQUIRED")));
    }

    private static Result<Album.Id> parseAlbumId(String value) {
        try {
            return Result.success(Album.Id.of(value));
        } catch (IllegalArgumentException e) {
            return Result.failure(
                    new ErrorResult(
                            "albumId",
                            "アルバムIDの形式が不正です: " + value,
                            "ALBUM_ID_INVALID"));
        }
    }

    private static Result<Optional<LabelTag>> resolveLabelTag(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> LabelTag.fromInput(v)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<LabelTag>>success(Optional.empty()));
    }

    private static Result<Optional<AlbumDistribution>> resolveDistribution(@Nullable DistributionInput input) {
        return Optional.ofNullable(input)
                .map(CreateAlbumArticleService::validateDistribution)
                .orElseGet(() -> Result.<Optional<AlbumDistribution>>success(Optional.empty()));
    }

    private static Result<Optional<AlbumDistribution>> validateDistribution(DistributionInput input) {
        return Result.zip(
                resolvePrice(input.physicalPrice()),
                resolvePrice(input.downloadPrice()),
                resolveUrl(input.demoUrl()),
                (physicalPrice, downloadPrice, demoUrl) -> AlbumDistribution.create(
                        physicalPrice.orElse(null),
                        downloadPrice.orElse(null),
                        demoUrl.orElse(null),
                        input.note()))
                .map(Optional::of);
    }

    private static Result<Optional<Price>> resolvePrice(@Nullable Integer amount) {
        return Optional.ofNullable(amount)
                .map(
                        a -> Price.fromInput(a)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<Price>>success(Optional.empty()));
    }

    private static Result<Optional<Url>> resolveUrl(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> Url.fromInput(v)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<Url>>success(Optional.empty()));
    }

    private static CreateAlbumArticleOutput toOutput(AlbumArticle article) {
        return new CreateAlbumArticleOutput(
                article.id().value(),
                article.introShort(),
                Optional.ofNullable(article.labelTag())
                        .map(LabelTag::name)
                        .orElse(null));
    }
}
