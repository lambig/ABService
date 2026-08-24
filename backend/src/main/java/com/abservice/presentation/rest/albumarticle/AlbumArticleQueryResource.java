package com.abservice.presentation.rest.albumarticle;

import com.abservice.application.query.albumarticle.GetAlbumArticleQuery;
import com.abservice.application.query.albumarticle.GetAlbumArticleResult;
import com.abservice.application.query.albumarticle.GetAlbumArticleService;
import com.abservice.application.query.albumarticle.ListAlbumArticlesQuery;
import com.abservice.application.query.albumarticle.ListAlbumArticlesResult;
import com.abservice.application.query.albumarticle.ListAlbumArticlesService;
import com.abservice.application.query.albumarticle.model.AlbumArticleView;
import com.abservice.presentation.rest.albumarticle.response.AlbumArticleListResponse;
import com.abservice.presentation.rest.albumarticle.response.AlbumArticleResponse;
import com.abservice.presentation.rest.exception.ProblemDetail;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * アルバム記事集約の Query REST リソース
 *
 * <p>
 * アルバム記事の詳細照会（GET）と一覧照会（GET、ページネーション付き）を受け付ける。未存在は例外ではなく
 * {@link GetAlbumArticleResult.NotFound} として扱い、404 を RFC 9457 Problem Details
 * （{@code application/problem+json}）で返す。アルバム記事は公開サイトが直接参照しない管理用マスタのため、参照も管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/album-articles")
@RolesAllowed(SecurityRoles.ADMIN)
public class AlbumArticleQueryResource {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final GetAlbumArticleService getAlbumArticleService;
    private final ListAlbumArticlesService listAlbumArticlesService;

    /**
     * @param getAlbumArticleService
     *            アルバム記事詳細照会ユースケース
     * @param listAlbumArticlesService
     *            アルバム記事一覧照会ユースケース
     */
    public AlbumArticleQueryResource(
            GetAlbumArticleService getAlbumArticleService,
            ListAlbumArticlesService listAlbumArticlesService) {
        this.getAlbumArticleService = getAlbumArticleService;
        this.listAlbumArticlesService = listAlbumArticlesService;
    }

    /**
     * アルバム記事詳細を照会します。
     *
     * @param id
     *            アルバム記事のドメインID（対応するAlbum集約のIDと同じ）
     * @return 200 とアルバム記事詳細、未存在時は 404 の Problem Details
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") String id) {
        return getAlbumArticleService.query(new GetAlbumArticleQuery(id))
                .map(result -> toResponse(result, id));
    }

    private static Response toResponse(GetAlbumArticleResult result, String id) {
        return switch (result) {
            case GetAlbumArticleResult.Found(var article) -> Response.ok(toArticleResponse(article)).build();
            case GetAlbumArticleResult.NotFound() -> Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.valueOf(PROBLEM_JSON)).entity(notFoundProblem(id)).build();
        };
    }

    private static ProblemDetail notFoundProblem(String id) {
        return ProblemDetail.of(
                "ENTITY_NOT_FOUND",
                "Resource not found",
                Response.Status.NOT_FOUND.getStatusCode(),
                "AlbumArticle not found: id=" + id,
                List.of());
    }

    private static AlbumArticleResponse toArticleResponse(AlbumArticleView view) {
        return new AlbumArticleResponse(
                view.albumId(),
                view.introLong(),
                view.introShort(),
                view.firstEventSpace(),
                view.labelTag());
    }

    /**
     * アルバム記事一覧を照会します（ページネーション付き）。
     *
     * @param page
     *            ページ番号（0始まり。デフォルト0）
     * @param size
     *            1ページの件数（デフォルト20、最大100）
     * @param sort
     *            並び順のキー（未指定なら登録の新しい順）
     * @param direction
     *            並び順の向き（未指定ならキーごとの既定）
     * @return 200 とアルバム記事一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") @Nullable String sort,
            @QueryParam("direction") @Nullable String direction) {
        return listAlbumArticlesService.query(
                new ListAlbumArticlesQuery(
                        page,
                        size,
                        sort,
                        direction))
                .map(AlbumArticleQueryResource::toListResponse);
    }

    private static Response toListResponse(ListAlbumArticlesResult result) {
        return Response.ok(
                new AlbumArticleListResponse(
                        result.items().stream().map(AlbumArticleQueryResource::toArticleResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }
}
