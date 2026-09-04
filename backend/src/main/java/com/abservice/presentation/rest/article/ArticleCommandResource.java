package com.abservice.presentation.rest.article;

import com.abservice.application.service.article.CreateArticleInput;
import com.abservice.application.service.article.CreateArticleOutput;
import com.abservice.application.service.article.CreateArticleService;
import com.abservice.application.service.article.DeleteArticleInput;
import com.abservice.application.service.article.DeleteArticleService;
import com.abservice.application.service.article.PublishArticleInput;
import com.abservice.application.service.article.PublishArticleOutput;
import com.abservice.application.service.article.PublishArticleService;
import com.abservice.application.service.article.RemoveArticleAlbumInput;
import com.abservice.application.service.article.RemoveArticleAlbumService;
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
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * 記事集約の Command REST リソース
 *
 * <p>
 * 記事の作成（POST）・更新（PUT、全項目置換）・削除（DELETE、べき等）・公開（POST .../publish）・非公開化（POST
 * .../unpublish）・アルバム紐付け（PUT .../album）・紐付け解除（DELETE .../album、べき等）を
 * 受け付ける。検証・永続化は アプリケーション層に委譲し、 検証失敗・対象不在は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details
 * に変換する（アルバム記事の公開時、参照先アルバムが非公開の場合や、ALBUM種別以外の記事への アルバム紐付けは
 * {@code BusinessRuleViolationException} 経由で 409）。全操作は管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/articles")
@RolesAllowed(SecurityRoles.ADMIN)
public class ArticleCommandResource {

    private final CreateArticleService createArticleService;
    private final UpdateArticleService updateArticleService;
    private final DeleteArticleService deleteArticleService;
    private final PublishArticleService publishArticleService;
    private final UnpublishArticleService unpublishArticleService;
    private final SetArticleAlbumService setArticleAlbumService;
    private final RemoveArticleAlbumService removeArticleAlbumService;

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
     * @param removeArticleAlbumService
     *            記事のAlbum参照解除ユースケース
     */
    public ArticleCommandResource(
            CreateArticleService createArticleService,
            UpdateArticleService updateArticleService,
            DeleteArticleService deleteArticleService,
            PublishArticleService publishArticleService,
            UnpublishArticleService unpublishArticleService,
            SetArticleAlbumService setArticleAlbumService,
            RemoveArticleAlbumService removeArticleAlbumService) {
        this.createArticleService = createArticleService;
        this.updateArticleService = updateArticleService;
        this.deleteArticleService = deleteArticleService;
        this.publishArticleService = publishArticleService;
        this.unpublishArticleService = unpublishArticleService;
        this.setArticleAlbumService = setArticleAlbumService;
        this.removeArticleAlbumService = removeArticleAlbumService;
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
    @ResponseStatus(RestResponse.StatusCode.CREATED)
    public Uni<CreateArticleResponse> create(CreateArticleRequest request) {
        return createArticleService.execute(toInput(request))
                .map(ArticleCommandResource::toResponse);
    }

    private static CreateArticleInput toInput(CreateArticleRequest request) {
        return new CreateArticleInput(
                request.articleType(),
                request.title(),
                request.body(),
                request.bodyFormat(),
                request.introShort());
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
    public Uni<UpdateArticleResponse> update(@PathParam("id") String id, UpdateArticleRequest request) {
        return updateArticleService.execute(toInput(id, request))
                .map(ArticleCommandResource::toResponse);
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
    public Uni<Void> delete(@PathParam("id") String id) {
        return deleteArticleService.execute(new DeleteArticleInput(id))
                .replaceWithVoid();
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
    public Uni<PublishArticleResponse> publish(@PathParam("id") String id) {
        return publishArticleService.execute(new PublishArticleInput(id))
                .map(ArticleCommandResource::toResponse);
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
    public Uni<UnpublishArticleResponse> unpublish(@PathParam("id") String id) {
        return unpublishArticleService.execute(new UnpublishArticleInput(id))
                .map(ArticleCommandResource::toResponse);
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
    public Uni<SetArticleAlbumResponse> setAlbum(@PathParam("id") String id, SetArticleAlbumRequest request) {
        return setArticleAlbumService.execute(new SetArticleAlbumInput(id, request.albumId()))
                .map(ArticleCommandResource::toResponse);
    }

    /**
     * 記事のアルバム紐付けを解除します（ALBUM種別の記事のみ）。
     *
     * <p>
     * 紐付けを持たない記事・参照が失効している記事に対してもべき等に成功します。参照先アルバムの削除に伴う失効とは別に、
     * 人が明示的に外す操作のため理由は残しません。
     * </p>
     *
     * @param id
     *            解除対象の記事ID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}/album")
    public Uni<Void> removeAlbum(@PathParam("id") String id) {
        return removeArticleAlbumService.execute(new RemoveArticleAlbumInput(id))
                .replaceWithVoid();
    }

    private static SetArticleAlbumResponse toResponse(SetArticleAlbumOutput output) {
        return new SetArticleAlbumResponse(
                output.articleId(),
                output.articleType(),
                output.albumId(),
                output.title());
    }
}
