package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * 記事非公開化コマンドサービス
 *
 * <p>
 * {@link Article#unpublish(com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。参照先Albumの公開状態に関わらず常に許可されます
 * （非公開Albumを参照する記事は公開できませんが、記事の非公開化自体はいつでも可能）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class UnpublishArticleService implements CommandService<UnpublishArticleInput, UnpublishArticleOutput> {

    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<UnpublishArticleOutput> execute(UnpublishArticleInput input) {
        return input.asValidated()
                .map(valid -> Article.Id.of(Objects.requireNonNull(valid.articleId())))
                .flatMap(this::findExisting)
                .flatMap(
                        existing -> businessDateTimeProvider.now()
                                .map(existing::unpublish))
                .flatMap(articleRepository::save)
                .map(UnpublishArticleService::toOutput);
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private static UnpublishArticleOutput toOutput(Article article) {
        return new UnpublishArticleOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value(),
                article.isPublic());
    }
}
