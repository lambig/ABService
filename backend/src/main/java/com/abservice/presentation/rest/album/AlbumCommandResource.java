package com.abservice.presentation.rest.album;

import com.abservice.application.service.album.CreateAlbumInput;
import com.abservice.application.service.album.CreateAlbumInput.EventInput;
import com.abservice.application.service.album.CreateAlbumOutput;
import com.abservice.application.service.album.CreateAlbumService;
import com.abservice.presentation.rest.album.request.CreateAlbumRequest;
import com.abservice.presentation.rest.album.request.CreateAlbumRequest.EventRequest;
import com.abservice.presentation.rest.album.response.CreateAlbumResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * アルバム集約の Command REST リソース
 *
 * <p>
 * アルバムの作成（POST）を受け付ける。検証・永続化はアプリケーション層に委譲し、検証失敗は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。
 * </p>
 */
@Path("/api/v1/albums")
public class AlbumCommandResource {

    private final CreateAlbumService createAlbumService;

    /**
     * @param createAlbumService
     *            アルバム作成ユースケース
     */
    public AlbumCommandResource(CreateAlbumService createAlbumService) {
        this.createAlbumService = createAlbumService;
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
}
