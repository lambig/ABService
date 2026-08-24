package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.ArticlePublicationService;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * 記事公開コマンドサービス
 *
 * <p>
 * 公開という操作そのものは {@link ArticlePublicationService} が担います（参照先アルバムの状態に依存するため
 * 記事単体では可否を判定できず、集約をまたぐ規則の適用と状態遷移を分離しない）。本サービスは取得・保存・出力の合成に徹し、
 * 「検証してから公開する」という順序の知識を持ちません。未存在は{@link EntityNotFoundException}（404）、
 * 規則違反は{@link BusinessRuleViolationException}（409）になります。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class PublishArticleService implements CommandService<PublishArticleInput, PublishArticleOutput> {

    private final ArticleRepository articleRepository;
    private final ArticlePublicationService articlePublicationService;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<PublishArticleOutput> execute(PublishArticleInput input) {
        return input.asValidated()
                .map(valid -> Article.Id.of(Objects.requireNonNull(valid.articleId())))
                .flatMap(this::findExisting)
                .flatMap(this::publish)
                .flatMap(articleRepository::save)
                .map(PublishArticleService::toOutput);
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private Uni<Article> publish(Article article) {
        return businessDateTimeProvider.now()
                .flatMap(now -> articlePublicationService.publish(article, now));
    }

    private static PublishArticleOutput toOutput(Article article) {
        return new PublishArticleOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value(),
                article.isPublic());
    }
}
