package com.abservice.presentation.rest.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.album.GetAlbumQuery;
import com.abservice.application.query.album.GetAlbumResult;
import com.abservice.application.query.album.GetAlbumService;
import com.abservice.application.query.album.ListAlbumsQuery;
import com.abservice.application.query.album.ListAlbumsService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.Nullable;

/**
 * アルバム集約の公開向け Query REST リソース
 *
 * <p>
 * アルバムの詳細照会（GET）と一覧照会（GET、ページネーション付き）を認証不要で受け付ける。公開中のアルバムのみを対象とし、
 * 下書きは未存在として扱う（下書きを含む照会は {@link AlbumAdminQueryResource}）。未存在は例外ではなく
 * {@link GetAlbumResult.NotFound} として扱い、404 を RFC 9457 Problem Details
 * （{@code application/problem+json}）で返す。
 * </p>
 */
@Path("/api/v1/albums")
public class AlbumQueryResource {

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
     * 公開中のアルバム詳細を照会します。
     *
     * @param id
     *            アルバムのドメインID
     * @return 200 とアルバム詳細、未存在時は 404 の Problem Details
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") String id) {
        return getAlbumService.query(
                new GetAlbumQuery(
                        id,
                        Audience.PUBLIC))
                .map(result -> AlbumQueryResponses.toResponse(result, id));
    }

    /**
     * 公開中のアルバム一覧を照会します（ページネーション付き）。
     *
     * @param page
     *            ページ番号（0始まり。デフォルト0）
     * @param size
     *            1ページの件数（デフォルト20、最大100）
     * @param sort
     *            並び順のキー（未指定なら登録の新しい順）
     * @param direction
     *            並び順の向き（未指定ならキーごとの既定）
     * @return 200 とアルバム一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") @Nullable String sort,
            @QueryParam("direction") @Nullable String direction) {
        return listAlbumsService.query(
                new ListAlbumsQuery(
                        page,
                        size,
                        Audience.PUBLIC,
                        sort,
                        direction))
                .map(AlbumQueryResponses::toListResponse);
    }
}
