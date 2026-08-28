package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.repository.article.ArticleTagRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;

/**
 * 記事タグ追加コマンドサービス
 *
 * <p>
 * {@link Article#addTag(ArticleTag, com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。
 * </p>
 *
 * <p>
 * タグは記事に属さず複数の記事が共有する語彙のため、名前で既存を引き当ててから付ける。同じ名前のタグが既にあれば
 * それを使い、無いときだけ新しいタグを作る。同定を行わずに新しいIDのタグを作ると {@code article_tag.name} の 一意制約に反する。
 * </p>
 *
 * <p>
 * 同じタグが既に付いている場合は Article 集約自身が
 * {@link com.abservice.domain.exception.BusinessRuleViolationException}（409）で拒む。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AddArticleTagService implements CommandService<AddArticleTagInput, AddArticleTagOutput> {

    private final ArticleRepository articleRepository;
    private final ArticleTagRepository articleTagRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<AddArticleTagOutput> execute(AddArticleTagInput input) {
        return Uni.createFrom()
                .item(() -> validate(input))
                .flatMap(
                        valid -> identified(valid.tag())
                                .flatMap(tag -> addTagTo(valid.articleId(), tag)));
    }

    private Uni<AddArticleTagOutput> addTagTo(Article.Id articleId, ArticleTag tag) {
        return findExisting(articleId)
                .flatMap(article -> tagged(article, tag))
                .flatMap(articleRepository::save)
                .map(saved -> toOutput(saved, tag));
    }

    private record ValidInput(Article.Id articleId, ArticleTag tag) {
    }

    private static ValidInput validate(AddArticleTagInput input) {
        return Result.zip(
                Article.Id.fromInput(input.articleId()),
                ArticleTag.fromInput(input.name()),
                ValidInput::new)
                .resolve(ValidationException::new);
    }

    /*
     * FIND-OR-CREATE: 同じ名前のタグは1つに保つ。既存があればそのIDのタグを、無ければ受け取った新規タグを使う。
     */
    private Uni<ArticleTag> identified(ArticleTag newTag) {
        return articleTagRepository.findByName(newTag.getName())
                .map(
                        existing -> Optional.ofNullable(existing)
                                .orElse(newTag));
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private Uni<Article> tagged(Article article, ArticleTag tag) {
        return businessDateTimeProvider.now()
                .map(now -> article.addTag(tag, now));
    }

    private static AddArticleTagOutput toOutput(Article article, ArticleTag tag) {
        return new AddArticleTagOutput(
                article.id().value(),
                tag.id().value(),
                tag.getName());
    }
}
