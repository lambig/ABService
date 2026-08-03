package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.article.ArticleRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * 記事削除コマンドサービス
 *
 * <p>
 * べき等な削除ユースケースです。対象記事の存在有無は確認せず、常に成功として扱います
 * （DELETEの一般的なべき等性に倣う）。ただし記事IDの形式検証は行い、不正な形式は {@link ValidationException}
 * として扱います。
 * </p>
 */
@ApplicationScoped
public class DeleteArticleService implements CommandService<DeleteArticleInput, DeleteArticleOutput> {

    private final ArticleRepository articleRepository;

    /**
     * @param articleRepository
     *            記事リポジトリ
     */
    public DeleteArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @WithTransaction
    @Override
    public Uni<DeleteArticleOutput> execute(DeleteArticleInput input) {
        return Uni.createFrom()
                .item(
                        () -> Article.Id.fromInput(input.articleId())
                                .resolve(ValidationException::new))
                .flatMap(articleRepository::deleteById)
                .replaceWith(new DeleteArticleOutput());
    }
}
