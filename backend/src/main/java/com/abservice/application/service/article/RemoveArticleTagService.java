package com.abservice.application.service.article;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 記事タグ削除コマンドサービス
 *
 * <p>
 * {@link Article#removeTag(ArticleTag.Id)} を呼び出すユースケースです。付いていないタグを外す操作はべき等に
 * 成功する（記事から見て結果が同じため）。タグの付け替えは記事そのものの更新ではないため、記事の更新日時は 動かしません。
 * </p>
 *
 * <p>
 * 外すのは記事とタグの結び付きだけで、タグ語彙そのものは残す。他の記事が同じタグを使っている場合があり、
 * 使われなくなったタグの掃除は本ユースケースの責務ではない。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class RemoveArticleTagService implements CommandService<RemoveArticleTagInput, RemoveArticleTagOutput> {

    private final ArticleRepository articleRepository;

    @WithTransaction
    @Override
    public Uni<RemoveArticleTagOutput> execute(RemoveArticleTagInput input) {
        return Uni.createFrom()
                .item(() -> validate(input))
                .flatMap(
                        ids -> findExisting(ids.articleId())
                                .map(article -> article.removeTag(ids.tagId()))
                                .flatMap(articleRepository::save)
                                .map(saved -> toOutput(saved, ids.tagId())));
    }

    private record Ids(Article.Id articleId, ArticleTag.Id tagId) {
    }

    private static Ids validate(RemoveArticleTagInput input) {
        return Result.zip(
                Article.Id.fromInput(input.articleId())
                        .mapErrorFields(field -> "articleId"),
                ArticleTag.Id.fromInput(input.tagId())
                        .mapErrorFields(field -> "tagId"),
                Ids::new)
                .resolve(ValidationException::new);
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private static RemoveArticleTagOutput toOutput(Article article, ArticleTag.Id tagId) {
        return new RemoveArticleTagOutput(article.id().value(), tagId.value());
    }
}
