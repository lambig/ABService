package com.abservice.presentation.rest.article;

import com.abservice.application.query.Audience;
import com.abservice.application.query.article.GetArticleQuery;
import com.abservice.application.query.article.GetArticleResult;
import com.abservice.application.query.article.GetArticleService;
import com.abservice.application.query.article.ListArticlesQuery;
import com.abservice.application.query.article.ListArticlesService;
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

/**
 * 記事集約の管理向け Query REST リソース
 *
 * <p>
 * 下書き（未公開）を含めた全記事の詳細照会（GET）と一覧照会（GET、ページネーション付き）を受け付ける。管理画面が公開前の記事を
 * 編集・確認するための経路であり、管理者ロール（{@code Authorization: Bearer <APIキー>}）を要求する。応答表現は
 * 公開向け（{@link ArticleQueryResource}）と同一で、{@code publicFlag} が false
 * のものが下書き。未存在は 例外ではなく {@link GetArticleResult.NotFound} として扱い、404 を RFC 9457
 * Problem Details （{@code application/problem+json}）で返す。
 * </p>
 */
@Path("/api/v1/admin/articles")
@RolesAllowed(SecurityRoles.ADMIN)
public class ArticleAdminQueryResource {

    private final GetArticleService getArticleService;
    private final ListArticlesService listArticlesService;

    /**
     * @param getArticleService
     *            記事詳細照会ユースケース
     * @param listArticlesService
     *            記事一覧照会ユースケース
     */
    public ArticleAdminQueryResource(GetArticleService getArticleService, ListArticlesService listArticlesService) {
        this.getArticleService = getArticleService;
        this.listArticlesService = listArticlesService;
    }

    /**
     * 下書きを含む記事詳細を照会します。
     *
     * @param id
     *            記事のドメインID
     * @return 200 と記事詳細、未存在時は 404 の Problem Details
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") String id) {
        return getArticleService.query(
                new GetArticleQuery(
                        id,
                        Audience.ADMIN))
                .map(result -> ArticleQueryResponses.toResponse(result, id));
    }

    /**
     * 下書きを含む記事一覧を照会します（ページネーション付き）。
     *
     * @param page
     *            ページ番号（0始まり。デフォルト0）
     * @param size
     *            1ページの件数（デフォルト20、最大100）
     * @return 200 と記事一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return listArticlesService.query(
                new ListArticlesQuery(
                        page,
                        size,
                        Audience.ADMIN))
                .map(ArticleQueryResponses::toListResponse);
    }
}
