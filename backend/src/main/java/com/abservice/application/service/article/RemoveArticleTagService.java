package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 記事タグ削除コマンドサービス
 *
 * <p>
 * {@link Article#removeTag(ArticleTag.Id, com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。付いていないタグを外す操作はべき等に成功する（記事から見て結果が同じため）。
 * </p>
 *
 * <p>
 * 外すのは記事とタグの結び付きだけで、タグ語彙そのものは残す。他の記事が同じタグを使っている場合があり、
 * 使われなくなったタグの掃除は本ユースケースの責務ではない。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class RemoveArticleTagService implements CommandService<RemoveArticleTagInput, RemoveArticleTagOutput> {

    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<RemoveArticleTagOutput> execute(RemoveArticleTagInput input) {
        return Uni.createFrom()
                .item(() -> validate(input))
                .flatMap(
                        ids -> findExisting(ids.articleId())
                                .flatMap(article -> untagged(article, ids.tagId()))
                                .flatMap(articleRepository::save)
                                .map(saved -> toOutput(saved, ids.tagId())));
    }

    private record Ids(Article.Id articleId, ArticleTag.Id tagId) {
    }

    private static Ids validate(RemoveArticleTagInput input) {
        return Result.zip(
                Article.Id.fromInput(input.articleId()),
                ArticleTag.Id.fromInput(input.tagId()),
                Ids::new)
                .resolve(ValidationException::new);
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private Uni<Article> untagged(Article article, ArticleTag.Id tagId) {
        return businessDateTimeProvider.now()
                .map(now -> article.removeTag(tagId, now));
    }

    private static RemoveArticleTagOutput toOutput(Article article, ArticleTag.Id tagId) {
        return new RemoveArticleTagOutput(article.id().value(), tagId.value());
    }
}
