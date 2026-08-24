package com.abservice.domain.service;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.aggregate.article.ArticlePublicationPolicy;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;

/**
 * 記事の公開とアルバム紐付けを担うドメインサービス
 *
 * <p>
 * {@link Article} は {@link Album} をIDでしか参照しないため、公開してよいか・紐付けてよいかを単体では判定できない。
 * 参照先を引いて {@link ArticlePublicationPolicy} へ渡し、規則を満たす場合にのみ状態遷移させる操作を本サービスが持つ。
 * </p>
 *
 * <p>
 * 遷移メソッド（{@code Article#publish} / {@code Article#setAlbumId}）は
 * {@code @CrossAggregateTransition} が付いており、ArchUnit
 * が「ドメインサービスからのみ呼び出せる」ことを強制する。 判定を経ずに公開する経路は、呼び出し側の規律ではなく構造で閉じている。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ArticlePublicationService implements DomainService {

    private final AlbumExistenceService albumExistenceService;

    /**
     * 記事を公開します。
     *
     * @param article
     *            公開対象の記事
     * @param currentDateTime
     *            現在日時
     * @return 公開された記事。参照先が未存在なら {@code EntityNotFoundException}、規則を満たさない場合は
     *         {@link BusinessRuleViolationException} で失敗する
     */
    public Uni<Article> publish(Article article, BusinessDateTime currentDateTime) {
        return referencedAlbum(article)
                .map(
                        album -> publishVerified(
                                article,
                                album,
                                currentDateTime));
    }

    /**
     * 記事にアルバムを紐付けます。
     *
     * @param article
     *            紐付け対象の記事
     * @param albumId
     *            紐付け先のアルバムID
     * @param currentDateTime
     *            現在日時
     * @return 紐付け後の記事。未存在なら {@code EntityNotFoundException}、規則を満たさない場合は
     *         {@link BusinessRuleViolationException} で失敗する
     */
    public Uni<Article> attachAlbum(
            Article article,
            Album.Id albumId,
            BusinessDateTime currentDateTime) {
        return albumExistenceService.findExisting(albumId)
                .map(
                        album -> attachVerified(
                                article,
                                album,
                                currentDateTime));
    }

    private Uni<Optional<Album>> referencedAlbum(Article article) {
        return article.albumReference().activeAlbumId()
                .map(this::findExistingAsOptional)
                .orElseGet(() -> Uni.createFrom().item(Optional.empty()));
    }

    private Uni<Optional<Album>> findExistingAsOptional(Album.Id albumId) {
        return albumExistenceService.findExisting(albumId)
                .map(Optional::of);
    }

    private static Article publishVerified(
            Article article,
            Optional<Album> referencedAlbum,
            BusinessDateTime currentDateTime) {
        return ArticlePublicationPolicy.publishable()
                .verify(
                        new ArticlePublicationPolicy.PublicationTarget(
                                article,
                                referencedAlbum.orElse(null)),
                        target -> target.article().publish(currentDateTime))
                .resolve(BusinessRuleViolationException::fromErrors);
    }

    private static Article attachVerified(
            Article article,
            Album album,
            BusinessDateTime currentDateTime) {
        return ArticlePublicationPolicy.attachable()
                .verify(
                        new ArticlePublicationPolicy.AttachmentTarget(article, album),
                        target -> target.article().setAlbumId(target.album().id(), currentDateTime))
                .resolve(BusinessRuleViolationException::fromErrors);
    }
}
