package com.abservice.presentation.rest.article;

import com.abservice.application.service.article.CreateArticleInput;
import com.abservice.application.service.article.CreateArticleOutput;
import com.abservice.application.service.article.CreateArticleService;
import com.abservice.presentation.rest.article.request.CreateArticleRequest;
import com.abservice.presentation.rest.article.response.CreateArticleResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * 記事集約の Command REST リソース
 *
 * <p>
 * 記事の作成（POST）を受け付ける。検証・永続化はアプリケーション層に委譲し、検証失敗は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。
 * </p>
 */
@Path("/api/v1/articles")
public class ArticleCommandResource {

    private final CreateArticleService createArticleService;

    /**
     * @param createArticleService
     *            記事作成ユースケース
     */
    public ArticleCommandResource(CreateArticleService createArticleService) {
        this.createArticleService = createArticleService;
    }

    /**
     * 記事を作成します。
     *
     * @param request
     *            記事作成リクエスト
     * @return 201 Created と作成結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> create(CreateArticleRequest request) {
        return createArticleService.execute(toInput(request))
                .map(ArticleCommandResource::toCreated);
    }

    private static CreateArticleInput toInput(CreateArticleRequest request) {
        return new CreateArticleInput(
                request.articleType(),
                request.title(),
                request.body(),
                request.bodyFormat(),
                request.introShort());
    }

    private static Response toCreated(CreateArticleOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static CreateArticleResponse toResponse(CreateArticleOutput output) {
        return new CreateArticleResponse(
                output.articleId(),
                output.articleType(),
                output.title(),
                output.publicFlag());
    }
}
