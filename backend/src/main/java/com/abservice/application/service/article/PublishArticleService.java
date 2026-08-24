package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.domain.service.PublicationConsistencyService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * 記事公開コマンドサービス
 *
 * <p>
 * {@link Article#publish(com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。公開してよいかどうかの判定（アルバム参照の状態と参照先の公開状態）は集約をまたぐ不変条件のため
 * {@link PublicationConsistencyService} に委ね、本サービスは取得・適用・保存・出力の合成に徹します。未存在は
 * {@link EntityNotFoundException}（404）、公開できない状態は{@link BusinessRuleViolationException}（409）
 * になります。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class PublishArticleService implements CommandService<PublishArticleInput, PublishArticleOutput> {

    private final ArticleRepository articleRepository;
    private final PublicationConsistencyService publicationConsistencyService;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<PublishArticleOutput> execute(PublishArticleInput input) {
        return input.asValidated()
                .map(valid -> Article.Id.of(Objects.requireNonNull(valid.articleId())))
                .flatMap(this::findExisting)
                .flatMap(publicationConsistencyService::requirePublishable)
                .flatMap(
                        publishable -> businessDateTimeProvider.now()
                                .map(publishable::publish))
                .flatMap(articleRepository::save)
                .map(PublishArticleService::toOutput);
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private static PublishArticleOutput toOutput(Article article) {
        return new PublishArticleOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value(),
                article.isPublic());
    }
}
