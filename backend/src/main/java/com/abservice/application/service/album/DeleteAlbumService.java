package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * アルバム削除コマンドサービス
 *
 * <p>
 * べき等な削除ユースケースです。対象アルバムの存在有無は確認せず、常に成功として扱います
 * （DELETEの一般的なべき等性に倣う）。ただしアルバムIDの形式検証は行い、不正な形式は {@link ValidationException}
 * として扱います。
 * </p>
 *
 * <p>
 * アルバムは物理削除するため、当該アルバムを参照していた {@link Article} は参照先を失います。同一トランザクション内で
 * 参照を失効させ（旧アルバムID・失効日時・理由を記録）、公開中だった記事は非公開へ戻します。「公開中の記事が存在しない
 * アルバムを参照する」状態を作らないためで、非公開化のカスケード（{@link UnpublishAlbumService}）と同じ考え方です。
 * 影響を受けた記事は {@link DeleteAlbumOutput#affectedArticles()} に含めて返します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class DeleteAlbumService implements CommandService<DeleteAlbumInput, DeleteAlbumOutput> {

    private final AlbumRepository albumRepository;
    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<DeleteAlbumOutput> execute(DeleteAlbumInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(this::deleteWithReferencingArticle);
    }

    private Uni<DeleteAlbumOutput> deleteWithReferencingArticle(Album.Id albumId) {
        return articleRepository.findByAlbumId(albumId)
                .flatMap(this::loseAlbumReference)
                .flatMap(affected -> deleteAlbum(albumId, affected))
                .map(DeleteAlbumOutput::new);
    }

    private Uni<List<DeleteAlbumOutput.AffectedArticle>> deleteAlbum(
            Album.Id albumId,
            List<DeleteAlbumOutput.AffectedArticle> affected) {
        return albumRepository.deleteById(albumId)
                .replaceWith(affected);
    }

    private Uni<List<DeleteAlbumOutput.AffectedArticle>> loseAlbumReference(@Nullable Article article) {
        return Optional.ofNullable(article)
                .map(referencing -> businessDateTimeProvider.now().flatMap(now -> detach(referencing, now)))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    private Uni<List<DeleteAlbumOutput.AffectedArticle>> detach(Article article, BusinessDateTime now) {
        return articleRepository.save(withoutAlbum(article, now))
                .map(saved -> List.of(toAffectedArticle(saved, article.isPublic())));
    }

    private static Article withoutAlbum(Article article, BusinessDateTime now) {
        return Optional.of(article)
                .filter(Article::isPublic)
                .map(published -> published.unpublish(now))
                .orElse(article)
                .loseAlbumReference(AlbumReferenceLostReason.ALBUM_DELETED, now);
    }

    private static DeleteAlbumOutput.AffectedArticle toAffectedArticle(Article article, boolean wasPublic) {
        return new DeleteAlbumOutput.AffectedArticle(
                article.id().value(),
                article.title().value(),
                wasPublic);
    }
}
