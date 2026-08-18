package com.abservice.presentation.rest.album;

import com.abservice.application.query.album.GetAlbumQuery;
import com.abservice.application.query.album.GetAlbumResult;
import com.abservice.application.query.album.GetAlbumService;
import com.abservice.application.query.album.ListAlbumsQuery;
import com.abservice.application.query.album.ListAlbumsResult;
import com.abservice.application.query.album.ListAlbumsService;
import com.abservice.application.query.album.model.AlbumView;
import com.abservice.presentation.rest.album.response.AlbumListResponse;
import com.abservice.presentation.rest.album.response.AlbumResponse;
import com.abservice.presentation.rest.exception.ProblemDetail;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * アルバム集約の Query REST リソース
 *
 * <p>
 * アルバムの詳細照会（GET）と一覧照会（GET、ページネーション付き）を受け付ける。未存在は例外ではなく
 * {@link GetAlbumResult.NotFound} として扱い、404 を RFC 9457 Problem Details
 * （{@code application/problem+json}）で返す。
 * </p>
 */
@Path("/api/v1/albums")
public class AlbumQueryResource {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final GetAlbumService getAlbumService;
    private final ListAlbumsService listAlbumsService;

    /**
     * @param getAlbumService
     *            アルバム詳細照会ユースケース
     * @param listAlbumsService
     *            アルバム一覧照会ユースケース
     */
    public AlbumQueryResource(GetAlbumService getAlbumService, ListAlbumsService listAlbumsService) {
        this.getAlbumService = getAlbumService;
        this.listAlbumsService = listAlbumsService;
    }

    /**
     * アルバム詳細を照会します。
     *
     * @param id
     *            アルバムのドメインID
     * @return 200 とアルバム詳細、未存在時は 404 の Problem Details
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") String id) {
        return getAlbumService.query(new GetAlbumQuery(id))
                .map(result -> toResponse(result, id));
    }

    private static Response toResponse(GetAlbumResult result, String id) {
        return switch (result) {
            case GetAlbumResult.Found(var album) -> Response.ok(toAlbumResponse(album)).build();
            case GetAlbumResult.NotFound() -> Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.valueOf(PROBLEM_JSON)).entity(notFoundProblem(id)).build();
        };
    }

    private static ProblemDetail notFoundProblem(String id) {
        return ProblemDetail.of(
                "ENTITY_NOT_FOUND",
                "Resource not found",
                Response.Status.NOT_FOUND.getStatusCode(),
                "Album not found: id=" + id,
                List.of());
    }

    private static AlbumResponse toAlbumResponse(AlbumView view) {
        return new AlbumResponse(
                view.albumId(),
                view.title(),
                view.releaseDate(),
                view.artistDisplayName(),
                view.artistSortKey(),
                view.catalogNumber(),
                view.isdn(),
                view.eventName(),
                view.eventDate(),
                view.eventPlace(),
                view.eventSpaceNumber(),
                view.eventNote());
    }

    /**
     * アルバム一覧を照会します（ページネーション付き）。
     *
     * @param page
     *            ページ番号（0始まり。デフォルト0）
     * @param size
     *            1ページの件数（デフォルト20、最大100）
     * @return 200 とアルバム一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return listAlbumsService.query(new ListAlbumsQuery(page, size))
                .map(AlbumQueryResource::toListResponse);
    }

    private static Response toListResponse(ListAlbumsResult result) {
        return Response.ok(
                new AlbumListResponse(
                        result.items().stream().map(AlbumQueryResource::toAlbumResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }
}
