package com.abservice.domain.service;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumAttachment;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.aggregate.article.ArticlePublication;
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
 * 本サービスは参照先を引いて操作オブジェクト（{@link ArticlePublication} /
 * {@link AlbumAttachment}）を組み立て、 実行する。規則の判定と遷移は操作オブジェクトが持ち、本サービスは取得と組み立てを担う。
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
                .map(album -> new ArticlePublication(article, album.orElse(null)))
                .map(publication -> publication.publish(currentDateTime));
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
                .map(album -> new AlbumAttachment(article, album))
                .map(attachment -> attachment.attach(currentDateTime));
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
}
