package com.abservice.application.service.album;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.domain.service.AlbumDeletionService;
import com.abservice.domain.service.AlbumDeletionService.AlbumDeletion;
import com.abservice.domain.service.AlbumDeletionService.ArticleChange;
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
@FailureContract({Failure.VALIDATION, Failure.CONFLICT})
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
                                .mapErrorFields(field -> "albumId")
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
                .map(deletion::apply)
                .flatMap(this::saveChanges);
    }

    private Uni<List<DeleteAlbumOutput.AffectedArticle>> saveChanges(List<ArticleChange> changes) {
        return articleRepository.saveAll(changes.stream().map(ArticleChange::article).toList())
                .replaceWith(() -> changes.stream().map(DeleteAlbumService::toAffectedArticle).toList());
    }

    private static DeleteAlbumOutput.AffectedArticle toAffectedArticle(ArticleChange change) {
        return new DeleteAlbumOutput.AffectedArticle(
                change.effect().article().id().value(),
                change.effect().article().title().value(),
                change.effect().becomesUnpublished());
    }
}
