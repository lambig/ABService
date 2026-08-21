package com.abservice.presentation.rest.albumarticle;

import com.abservice.application.service.albumarticle.CreateAlbumArticleInput;
import com.abservice.application.service.albumarticle.CreateAlbumArticleInput.DistributionInput;
import com.abservice.application.service.albumarticle.CreateAlbumArticleOutput;
import com.abservice.application.service.albumarticle.CreateAlbumArticleService;
import com.abservice.application.service.albumarticle.DeleteAlbumArticleInput;
import com.abservice.application.service.albumarticle.DeleteAlbumArticleService;
import com.abservice.application.service.albumarticle.UpdateAlbumArticleInput;
import com.abservice.application.service.albumarticle.UpdateAlbumArticleOutput;
import com.abservice.application.service.albumarticle.UpdateAlbumArticleService;
import com.abservice.presentation.rest.albumarticle.request.CreateAlbumArticleRequest;
import com.abservice.presentation.rest.albumarticle.request.CreateAlbumArticleRequest.DistributionRequest;
import com.abservice.presentation.rest.albumarticle.request.UpdateAlbumArticleRequest;
import com.abservice.presentation.rest.albumarticle.response.CreateAlbumArticleResponse;
import com.abservice.presentation.rest.albumarticle.response.UpdateAlbumArticleResponse;
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
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事集約の Command REST リソース
 *
 * <p>
 * アルバム記事の作成（POST）・更新（PUT、全項目置換）・削除（DELETE、べき等）を受け付ける。検証・永続化は
 * アプリケーション層に委譲し、検証失敗・対象不在は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。全操作は管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/album-articles")
@RolesAllowed(SecurityRoles.ADMIN)
public class AlbumArticleCommandResource {

    private final CreateAlbumArticleService createAlbumArticleService;
    private final UpdateAlbumArticleService updateAlbumArticleService;
    private final DeleteAlbumArticleService deleteAlbumArticleService;

    /**
     * @param createAlbumArticleService
     *            アルバム記事作成ユースケース
     * @param updateAlbumArticleService
     *            アルバム記事更新ユースケース
     * @param deleteAlbumArticleService
     *            アルバム記事削除ユースケース
     */
    public AlbumArticleCommandResource(
            CreateAlbumArticleService createAlbumArticleService,
            UpdateAlbumArticleService updateAlbumArticleService,
            DeleteAlbumArticleService deleteAlbumArticleService) {
        this.createAlbumArticleService = createAlbumArticleService;
        this.updateAlbumArticleService = updateAlbumArticleService;
        this.deleteAlbumArticleService = deleteAlbumArticleService;
    }

    /**
     * アルバム記事を作成します。
     *
     * @param request
     *            アルバム記事作成リクエスト
     * @return 201 Created と作成結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> create(CreateAlbumArticleRequest request) {
        return createAlbumArticleService.execute(toInput(request))
                .map(AlbumArticleCommandResource::toCreated);
    }

    private static CreateAlbumArticleInput toInput(CreateAlbumArticleRequest request) {
        return new CreateAlbumArticleInput(
                request.albumId(),
                request.introLong(),
                request.introShort(),
                request.firstEventSpace(),
                request.labelTag(),
                toDistributionInput(request.distribution()));
    }

    private static @Nullable DistributionInput toDistributionInput(@Nullable DistributionRequest distribution) {
        return Optional.ofNullable(distribution)
                .map(
                        d -> new DistributionInput(
                                d.physicalPrice(),
                                d.downloadPrice(),
                                d.demoUrl(),
                                d.note()))
                .orElse(null);
    }

    private static Response toCreated(CreateAlbumArticleOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static CreateAlbumArticleResponse toResponse(CreateAlbumArticleOutput output) {
        return new CreateAlbumArticleResponse(
                output.albumId(),
                output.introShort(),
                output.labelTag());
    }

    /**
     * アルバム記事を更新します（PUT風の全項目置換）。
     *
     * @param id
     *            更新対象のアルバム記事ID（対応するAlbum集約のID）
     * @param request
     *            アルバム記事更新リクエスト
     * @return 200 OK と更新結果
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("id") String id, UpdateAlbumArticleRequest request) {
        return updateAlbumArticleService.execute(toInput(id, request))
                .map(AlbumArticleCommandResource::toOk);
    }

    private static UpdateAlbumArticleInput toInput(String id, UpdateAlbumArticleRequest request) {
        return new UpdateAlbumArticleInput(
                id,
                request.introLong(),
                request.introShort(),
                request.firstEventSpace(),
                request.labelTag(),
                toDistributionInput(request.distribution()));
    }

    private static UpdateAlbumArticleInput.@Nullable DistributionInput toDistributionInput(
            UpdateAlbumArticleRequest.@Nullable DistributionRequest distribution) {
        return Optional.ofNullable(distribution)
                .map(
                        d -> new UpdateAlbumArticleInput.DistributionInput(
                                d.physicalPrice(),
                                d.downloadPrice(),
                                d.demoUrl(),
                                d.note()))
                .orElse(null);
    }

    private static Response toOk(UpdateAlbumArticleOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UpdateAlbumArticleResponse toResponse(UpdateAlbumArticleOutput output) {
        return new UpdateAlbumArticleResponse(
                output.albumId(),
                output.introShort(),
                output.labelTag());
    }

    /**
     * アルバム記事を削除します（べき等。対象アルバム記事の存在有無を問わず204を返す）。
     *
     * @param id
     *            削除対象のアルバム記事ID（対応するAlbum集約のID）
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") String id) {
        return deleteAlbumArticleService.execute(new DeleteAlbumArticleInput(id))
                .replaceWith(Response.noContent().build());
    }
}
