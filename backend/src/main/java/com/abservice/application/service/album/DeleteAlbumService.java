package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.domain.service.AlbumDeletionService;
import com.abservice.domain.service.AlbumDeletionService.AlbumDeletion;
import com.abservice.domain.service.AlbumDeletionService.ArticleEffect;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;

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
 * アルバムは物理削除するため、当該アルバムを参照していた {@link Article} は参照先を失います。1つのアルバムは
 * 複数の記事から参照されうるため、参照していた記事すべてについて同一トランザクション内で参照を失効させ
 * （旧アルバムID・失効日時・理由を記録）、公開中だった記事は非公開へ戻します。「公開中の記事が存在しない
 * アルバムを参照する」状態を作らないためで、非公開化のカスケード（{@link UnpublishAlbumService}）と同じ考え方です。
 * 影響を受けた記事は {@link DeleteAlbumOutput#affectedArticles()} に含めて返します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class DeleteAlbumService implements CommandService<DeleteAlbumInput, DeleteAlbumOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumAccessService albumAccessService;
    private final AlbumDeletionService albumDeletionService;
    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<DeleteAlbumOutput> execute(DeleteAlbumInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(this::deleteWithReferencingArticles);
    }

    private Uni<DeleteAlbumOutput> deleteWithReferencingArticles(Album.Id albumId) {
        return albumAccessService.findAndClaimEditIfPresent(albumId)
                .flatMap(this::detachReferencing)
                .flatMap(affected -> deleteAlbum(albumId, affected))
                .map(DeleteAlbumOutput::new);
    }

    private Uni<List<DeleteAlbumOutput.AffectedArticle>> detachReferencing(Optional<Album> claimed) {
        return claimed
                .map(Album::id)
                .map(albumDeletionService::attempt)
                .map(attempt -> attempt.flatMap(this::loseAlbumReferences))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    private Uni<List<DeleteAlbumOutput.AffectedArticle>> deleteAlbum(
            Album.Id albumId,
            List<DeleteAlbumOutput.AffectedArticle> affected) {
        return albumRepository.deleteById(albumId)
                .replaceWith(affected);
    }

    private Uni<List<DeleteAlbumOutput.AffectedArticle>> loseAlbumReferences(AlbumDeletion deletion) {
        return businessDateTimeProvider.now()
                .map(now -> detachedAll(deletion, now))
                .flatMap(articleRepository::saveAll)
                .replaceWith(() -> toAffectedArticles(deletion));
    }

    private static List<Article> detachedAll(AlbumDeletion deletion, BusinessDateTime now) {
        return deletion.effects().stream()
                .map(effect -> withoutAlbum(effect, now))
                .toList();
    }

    /**
     * 判定に従って遷移を当てる。何が起きるかは {@link AlbumDeletion} が決め、ここは適用に徹する。
     */
    private static Article withoutAlbum(ArticleEffect effect, BusinessDateTime now) {
        return Optional.of(effect)
                .filter(ArticleEffect::losesAlbumReference)
                .map(losing -> withoutAlbumReference(unpublishedIfNeeded(losing, now), now))
                .orElseGet(() -> unpublishedIfNeeded(effect, now));
    }

    private static Article unpublishedIfNeeded(ArticleEffect effect, BusinessDateTime now) {
        return Optional.of(effect)
                .filter(ArticleEffect::becomesUnpublished)
                .map(ArticleEffect::article)
                .map(published -> published.unpublish(now))
                .orElseGet(effect::article);
    }

    /*
     * NARROWING: 参照を失効させられるのは AlbumArticle だけで、他の種別は参照という概念を持たない。判定は
     * losesAlbumReference が持つが、型で絞れなかった場合は非公開化までを反映して返す。
     */
    private static Article withoutAlbumReference(Article article, BusinessDateTime now) {
        return AlbumArticle.from(article)
                .<Article>map(
                        albumArticle -> albumArticle.loseAlbumReference(
                                AlbumReferenceLostReason.ALBUM_DELETED,
                                now))
                .orElse(article);
    }

    private static List<DeleteAlbumOutput.AffectedArticle> toAffectedArticles(AlbumDeletion deletion) {
        return deletion.referencingArticles().stream()
                .map(DeleteAlbumService::toAffectedArticle)
                .toList();
    }

    private static DeleteAlbumOutput.AffectedArticle toAffectedArticle(Article article) {
        return new DeleteAlbumOutput.AffectedArticle(
                article.id().value(),
                article.title().value(),
                article.isPublic());
    }
}
