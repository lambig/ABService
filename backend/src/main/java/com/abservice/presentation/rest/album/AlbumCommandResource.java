package com.abservice.presentation.rest.album;

import com.abservice.application.service.album.CreateAlbumInput;
import com.abservice.application.service.album.CreateAlbumInput.EventInput;
import com.abservice.application.service.album.CreateAlbumOutput;
import com.abservice.application.service.album.CreateAlbumService;
import com.abservice.application.service.album.DeleteAlbumInput;
import com.abservice.application.service.album.DeleteAlbumService;
import com.abservice.application.service.album.UpdateAlbumInput;
import com.abservice.application.service.album.UpdateAlbumOutput;
import com.abservice.application.service.album.UpdateAlbumService;
import com.abservice.presentation.rest.album.request.CreateAlbumRequest;
import com.abservice.presentation.rest.album.request.CreateAlbumRequest.EventRequest;
import com.abservice.presentation.rest.album.request.UpdateAlbumRequest;
import com.abservice.presentation.rest.album.response.CreateAlbumResponse;
import com.abservice.presentation.rest.album.response.UpdateAlbumResponse;
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
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * アルバム集約の Command REST リソース
 *
 * <p>
 * アルバムの作成（POST）・更新（PUT、全項目置換）・削除（DELETE、べき等）を受け付ける。検証・永続化は
 * アプリケーション層に委譲し、検証失敗・対象不在は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。
 * </p>
 */
@Path("/api/v1/albums")
public class AlbumCommandResource {

    private final CreateAlbumService createAlbumService;
    private final UpdateAlbumService updateAlbumService;
    private final DeleteAlbumService deleteAlbumService;

    /**
     * @param createAlbumService
     *            アルバム作成ユースケース
     * @param updateAlbumService
     *            アルバム更新ユースケース
     * @param deleteAlbumService
     *            アルバム削除ユースケース
     */
    public AlbumCommandResource(
            CreateAlbumService createAlbumService,
            UpdateAlbumService updateAlbumService,
            DeleteAlbumService deleteAlbumService) {
        this.createAlbumService = createAlbumService;
        this.updateAlbumService = updateAlbumService;
        this.deleteAlbumService = deleteAlbumService;
    }

    /**
     * アルバムを作成します。
     *
     * @param request
     *            アルバム作成リクエスト
     * @return 201 Created と作成結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> create(CreateAlbumRequest request) {
        return createAlbumService.execute(toInput(request))
                .map(AlbumCommandResource::toCreated);
    }

    private static CreateAlbumInput toInput(CreateAlbumRequest request) {
        return new CreateAlbumInput(
                request.title(),
                request.releaseDate(),
                request.artistDisplayName(),
                request.artistSortKey(),
                request.catalogNumber(),
                request.isdn(),
                toEventInput(request.event()));
    }

    private static @Nullable EventInput toEventInput(@Nullable EventRequest event) {
        return Optional.ofNullable(event)
                .map(
                        e -> new EventInput(
                                e.name(),
                                e.date(),
                                e.place(),
                                e.spaceNumber(),
                                e.note()))
                .orElse(null);
    }

    private static Response toCreated(CreateAlbumOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static CreateAlbumResponse toResponse(CreateAlbumOutput output) {
        return new CreateAlbumResponse(
                output.albumId(),
                output.title(),
                output.releaseDate(),
                output.artistDisplayName());
    }

    /**
     * アルバムを更新します（PUT風の全項目置換）。
     *
     * @param id
     *            更新対象のアルバムID
     * @param request
     *            アルバム更新リクエスト
     * @return 200 OK と更新結果
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("id") String id, UpdateAlbumRequest request) {
        return updateAlbumService.execute(toInput(id, request))
                .map(AlbumCommandResource::toOk);
    }

    private static UpdateAlbumInput toInput(String id, UpdateAlbumRequest request) {
        return new UpdateAlbumInput(
                id,
                request.title(),
                request.releaseDate(),
                request.artistDisplayName(),
                request.artistSortKey(),
                request.catalogNumber(),
                request.isdn(),
                toEventInput(request.event()));
    }

    private static UpdateAlbumInput.@Nullable EventInput toEventInput(
            UpdateAlbumRequest.@Nullable EventRequest event) {
        return Optional.ofNullable(event)
                .map(
                        e -> new UpdateAlbumInput.EventInput(
                                e.name(),
                                e.date(),
                                e.place(),
                                e.spaceNumber(),
                                e.note()))
                .orElse(null);
    }

    private static Response toOk(UpdateAlbumOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UpdateAlbumResponse toResponse(UpdateAlbumOutput output) {
        return new UpdateAlbumResponse(
                output.albumId(),
                output.title(),
                output.releaseDate(),
                output.artistDisplayName());
    }

    /**
     * アルバムを削除します（べき等。対象アルバムの存在有無を問わず204を返す）。
     *
     * @param id
     *            削除対象のアルバムID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") String id) {
        return deleteAlbumService.execute(new DeleteAlbumInput(id))
                .replaceWith(Response.noContent().build());
    }
}
