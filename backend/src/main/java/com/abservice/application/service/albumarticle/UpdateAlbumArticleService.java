package com.abservice.application.service.albumarticle;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.albumarticle.UpdateAlbumArticleInput.DistributionInput;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.aggregate.albumarticle.AlbumDistribution;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.repository.albumarticle.AlbumArticleRepository;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事更新コマンドサービス
 *
 * <p>
 * 外部入力（{@link UpdateAlbumArticleInput}）から既存 {@link AlbumArticle} のCreate相当フィールド
 * （introLong/introShort/firstEventSpace/labelTag/distribution）をPUT風に全項目置換する
 * ユースケースです。入手経路は対象外のため既存の値をそのまま維持します。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して既存 {@code AlbumArticle}
 * を更新後の状態へ組み替えるオーケストレーションに徹します。検証失敗は {@link ValidationException} に、対象アルバム記事の不在は
 * {@link EntityNotFoundException} に集約し、HTTP への変換は presentation 層の
 * ExceptionMapper が担います。
 * </p>
 */
@ApplicationScoped
public class UpdateAlbumArticleService implements CommandService<UpdateAlbumArticleInput, UpdateAlbumArticleOutput> {

    private final AlbumArticleRepository albumArticleRepository;

    /**
     * @param albumArticleRepository
     *            アルバム記事リポジトリ
     */
    public UpdateAlbumArticleService(AlbumArticleRepository albumArticleRepository) {
        this.albumArticleRepository = albumArticleRepository;
    }

    @WithTransaction
    @Override
    public Uni<UpdateAlbumArticleOutput> execute(UpdateAlbumArticleInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(this::findExisting)
                .map(
                        existing -> validateAndApply(existing, input)
                                .resolve(ValidationException::new))
                .flatMap(albumArticleRepository::save)
                .map(UpdateAlbumArticleService::toOutput);
    }

    private Uni<AlbumArticle> findExisting(Album.Id id) {
        return albumArticleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("AlbumArticle", id.value()));
    }

    static Result<AlbumArticle> validateAndApply(AlbumArticle existing, UpdateAlbumArticleInput input) {
        return Result.zip(
                resolveLabelTag(input.labelTag()),
                resolveDistribution(input.distribution()),
                (labelTag, distribution) -> existing.updateIntro(input.introLong(), input.introShort())
                        .changeFirstEventSpace(input.firstEventSpace())
                        .updateLabelTag(labelTag.orElse(null))
                        .setDistribution(distribution.orElse(null)));
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
                .map(UpdateAlbumArticleService::validateDistribution)
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

    private static UpdateAlbumArticleOutput toOutput(AlbumArticle article) {
        return new UpdateAlbumArticleOutput(
                article.id().value(),
                article.introShort(),
                Optional.ofNullable(article.labelTag())
                        .map(LabelTag::name)
                        .orElse(null));
    }
}
