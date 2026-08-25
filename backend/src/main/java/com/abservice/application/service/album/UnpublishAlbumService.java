package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.AlbumExistenceService;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * アルバム非公開化コマンドサービス
 *
 * <p>
 * {@link Album#unpublish()} を呼び出すユースケースです。既に公開済みの記事が参照しているアルバムを
 * 非公開に戻すことは許可しますが、「公開中の記事が非公開アルバムを参照する」という不整合状態を作らないため、
 * 当該アルバムを参照する公開中の{@link Article}を同一トランザクション内ですべて連動して非公開化します
 * （カスケード非公開）。1つのアルバムは複数の記事から参照されうるため、対象は1件とは限りません。連動して
 * 非公開化した記事は{@link UnpublishAlbumOutput#cascadeUnpublishedArticles()}に含めて返します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class UnpublishAlbumService implements CommandService<UnpublishAlbumInput, UnpublishAlbumOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumExistenceService albumExistenceService;
    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<UnpublishAlbumOutput> execute(UnpublishAlbumInput input) {
        return input.asValidated()
                .map(valid -> Album.Id.of(Objects.requireNonNull(valid.albumId())))
                .flatMap(albumExistenceService::findExisting)
                .map(Album::unpublish)
                .flatMap(albumRepository::save)
                .flatMap(this::cascadeUnpublishReferencingArticles);
    }

    private Uni<UnpublishAlbumOutput> cascadeUnpublishReferencingArticles(Album album) {
        return articleRepository.findByAlbumId(album.id())
                .flatMap(this::unpublishPublicOnes)
                .map(cascadeUnpublished -> toOutput(album, cascadeUnpublished));
    }

    private Uni<List<Article>> unpublishPublicOnes(List<Article> referencing) {
        return businessDateTimeProvider.now()
                .map(now -> unpublished(referencing, now))
                .flatMap(articleRepository::saveAll);
    }

    private static List<Article> unpublished(List<Article> referencing, BusinessDateTime now) {
        return referencing.stream()
                .filter(Article::isPublic)
                .map(publicArticle -> publicArticle.unpublish(now))
                .toList();
    }

    private static UnpublishAlbumOutput toOutput(Album album, List<Article> cascadeUnpublished) {
        return new UnpublishAlbumOutput(
                album.id().value(),
                album.title().value(),
                album.isPublished(),
                cascadeUnpublished.stream()
                        .map(UnpublishAlbumService::toCascadeUnpublishedArticle)
                        .toList());
    }

    private static UnpublishAlbumOutput.CascadeUnpublishedArticle toCascadeUnpublishedArticle(Article article) {
        return new UnpublishAlbumOutput.CascadeUnpublishedArticle(
                article.id().value(),
                article.title().value());
    }
}
