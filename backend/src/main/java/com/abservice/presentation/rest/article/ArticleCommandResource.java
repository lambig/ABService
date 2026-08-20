package com.abservice.presentation.rest.article;

import com.abservice.application.service.article.CreateArticleInput;
import com.abservice.application.service.article.CreateArticleOutput;
import com.abservice.application.service.article.CreateArticleService;
import com.abservice.application.service.article.DeleteArticleInput;
import com.abservice.application.service.article.DeleteArticleService;
import com.abservice.application.service.article.PublishArticleInput;
import com.abservice.application.service.article.PublishArticleOutput;
import com.abservice.application.service.article.PublishArticleService;
import com.abservice.application.service.article.SetArticleAlbumInput;
import com.abservice.application.service.article.SetArticleAlbumOutput;
import com.abservice.application.service.article.SetArticleAlbumService;
import com.abservice.application.service.article.UnpublishArticleInput;
import com.abservice.application.service.article.UnpublishArticleOutput;
import com.abservice.application.service.article.UnpublishArticleService;
import com.abservice.application.service.article.UpdateArticleInput;
import com.abservice.application.service.article.UpdateArticleOutput;
import com.abservice.application.service.article.UpdateArticleService;
import com.abservice.presentation.rest.article.request.CreateArticleRequest;
import com.abservice.presentation.rest.article.request.SetArticleAlbumRequest;
import com.abservice.presentation.rest.article.request.UpdateArticleRequest;
import com.abservice.presentation.rest.article.response.CreateArticleResponse;
import com.abservice.presentation.rest.article.response.PublishArticleResponse;
import com.abservice.presentation.rest.article.response.SetArticleAlbumResponse;
import com.abservice.presentation.rest.article.response.UnpublishArticleResponse;
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
 * 記事の作成（POST）・更新（PUT、全項目置換）・削除（DELETE、べき等）・公開（POST .../publish）・非公開化（POST
 * .../unpublish）・アルバム紐付け（PUT .../album）を受け付ける。検証・永続化は アプリケーション層に委譲し、 検証失敗・対象不在は
 * {@code DomainException} 経由で {@code DomainExceptionMapper} が RFC 9457 Problem
 * Details に変換する（アルバム記事の公開時、参照先アルバムが非公開の場合や、ALBUM種別以外の記事への アルバム紐付けは
 * {@code BusinessRuleViolationException} 経由で 409）。
 * </p>
 */
@Path("/api/v1/articles")
public class ArticleCommandResource {

    private final CreateArticleService createArticleService;
    private final UpdateArticleService updateArticleService;
    private final DeleteArticleService deleteArticleService;
    private final PublishArticleService publishArticleService;
    private final UnpublishArticleService unpublishArticleService;
    private final SetArticleAlbumService setArticleAlbumService;

    /**
     * @param createArticleService
     *            記事作成ユースケース
     * @param updateArticleService
     *            記事更新ユースケース
     * @param deleteArticleService
     *            記事削除ユースケース
     * @param publishArticleService
     *            記事公開ユースケース
     * @param unpublishArticleService
     *            記事非公開化ユースケース
     * @param setArticleAlbumService
     *            記事へのAlbum参照設定ユースケース
     */
    public ArticleCommandResource(
            CreateArticleService createArticleService,
            UpdateArticleService updateArticleService,
            DeleteArticleService deleteArticleService,
            PublishArticleService publishArticleService,
            UnpublishArticleService unpublishArticleService,
            SetArticleAlbumService setArticleAlbumService) {
        this.createArticleService = createArticleService;
        this.updateArticleService = updateArticleService;
        this.deleteArticleService = deleteArticleService;
        this.publishArticleService = publishArticleService;
        this.unpublishArticleService = unpublishArticleService;
        this.setArticleAlbumService = setArticleAlbumService;
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

    /**
     * 記事を公開します。アルバム記事の場合、参照先アルバムが非公開だと409を返します。
     *
     * @param id
     *            公開対象の記事ID
     * @return 200 OK と公開結果
     */
    @POST
    @Path("/{id}/publish")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> publish(@PathParam("id") String id) {
        return publishArticleService.execute(new PublishArticleInput(id))
                .map(ArticleCommandResource::toOk);
    }

    private static Response toOk(PublishArticleOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static PublishArticleResponse toResponse(PublishArticleOutput output) {
        return new PublishArticleResponse(
                output.articleId(),
                output.articleType(),
                output.title(),
                output.publicFlag());
    }

    /**
     * 記事を非公開化します。
     *
     * @param id
     *            非公開化対象の記事ID
     * @return 200 OK と非公開化結果
     */
    @POST
    @Path("/{id}/unpublish")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> unpublish(@PathParam("id") String id) {
        return unpublishArticleService.execute(new UnpublishArticleInput(id))
                .map(ArticleCommandResource::toOk);
    }

    private static Response toOk(UnpublishArticleOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UnpublishArticleResponse toResponse(UnpublishArticleOutput output) {
        return new UnpublishArticleResponse(
                output.articleId(),
                output.articleType(),
                output.title(),
                output.publicFlag());
    }

    /**
     * 記事にアルバムを紐付けます（ALBUM種別の記事のみ。参照先アルバムの公開状態は問いません）。
     *
     * @param id
     *            紐付け対象の記事ID
     * @param request
     *            Album参照設定リクエスト
     * @return 200 OK と紐付け結果
     */
    @PUT
    @Path("/{id}/album")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> setAlbum(@PathParam("id") String id, SetArticleAlbumRequest request) {
        return setArticleAlbumService.execute(new SetArticleAlbumInput(id, request.albumId()))
                .map(ArticleCommandResource::toOk);
    }

    private static Response toOk(SetArticleAlbumOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static SetArticleAlbumResponse toResponse(SetArticleAlbumOutput output) {
        return new SetArticleAlbumResponse(
                output.articleId(),
                output.articleType(),
                output.albumId(),
                output.title());
    }
}
