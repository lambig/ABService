package com.abservice.application.service.article;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.repository.article.ArticleRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

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
@AllArgsConstructor
@FailureContract(Failure.VALIDATION)
public class DeleteArticleService implements CommandService<DeleteArticleInput, DeleteArticleOutput> {

    private final ArticleRepository articleRepository;

    @WithTransaction
    @Override
    public Uni<DeleteArticleOutput> execute(DeleteArticleInput input) {
        return input.asValidated(DeleteArticleInput::parsed)
                .flatMap(articleRepository::deleteById)
                .replaceWith(new DeleteArticleOutput());
    }
}
