package com.abservice.presentation.rest.article;

import com.abservice.application.service.article.CreateArticleInput;
import com.abservice.application.service.article.CreateArticleOutput;
import com.abservice.application.service.article.CreateArticleService;
import com.abservice.application.service.article.DeleteArticleInput;
import com.abservice.application.service.article.DeleteArticleService;
import com.abservice.application.service.article.UpdateArticleInput;
import com.abservice.application.service.article.UpdateArticleOutput;
import com.abservice.application.service.article.UpdateArticleService;
import com.abservice.presentation.rest.article.request.CreateArticleRequest;
import com.abservice.presentation.rest.article.request.UpdateArticleRequest;
import com.abservice.presentation.rest.article.response.CreateArticleResponse;
import com.abservice.presentation.rest.article.response.UpdateArticleResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * 記事集約の Command REST リソース
 *
 * <p>
 * 記事の作成（POST）・更新（PUT、全項目置換）・削除（DELETE、べき等）を受け付ける。検証・永続化は
 * アプリケーション層に委譲し、検証失敗・対象不在は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。
 * </p>
 */
@Path("/api/v1/articles")
public class ArticleCommandResource {

    private final CreateArticleService createArticleService;
    private final UpdateArticleService updateArticleService;
    private final DeleteArticleService deleteArticleService;

    /**
     * @param createArticleService
     *            記事作成ユースケース
     * @param updateArticleService
     *            記事更新ユースケース
     * @param deleteArticleService
     *            記事削除ユースケース
     */
    public ArticleCommandResource(
            CreateArticleService createArticleService,
            UpdateArticleService updateArticleService,
            DeleteArticleService deleteArticleService) {
        this.createArticleService = createArticleService;
        this.updateArticleService = updateArticleService;
        this.deleteArticleService = deleteArticleService;
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

    /**
     * 記事を更新します（PUT風の全項目置換）。
     *
     * @param id
     *            更新対象の記事ID
     * @param request
     *            記事更新リクエスト
     * @return 200 OK と更新結果
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("id") String id, UpdateArticleRequest request) {
        return updateArticleService.execute(toInput(id, request))
                .map(ArticleCommandResource::toOk);
    }

    private static UpdateArticleInput toInput(String id, UpdateArticleRequest request) {
        return new UpdateArticleInput(
                id,
                request.articleType(),
                request.title(),
                request.body(),
                request.bodyFormat(),
                request.introShort());
    }

    private static Response toOk(UpdateArticleOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UpdateArticleResponse toResponse(UpdateArticleOutput output) {
        return new UpdateArticleResponse(
                output.articleId(),
                output.articleType(),
                output.title(),
                output.publicFlag());
    }

    /**
     * 記事を削除します（べき等。対象記事の存在有無を問わず204を返す）。
     *
     * @param id
     *            削除対象の記事ID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") String id) {
        return deleteArticleService.execute(new DeleteArticleInput(id))
                .replaceWith(Response.noContent().build());
    }
}
