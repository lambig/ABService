package com.abservice.presentation.rest.article;

import com.abservice.application.query.article.GetArticleQuery;
import com.abservice.application.query.article.GetArticleResult;
import com.abservice.application.query.article.GetArticleService;
import com.abservice.application.query.article.ListArticlesQuery;
import com.abservice.application.query.article.ListArticlesResult;
import com.abservice.application.query.article.ListArticlesService;
import com.abservice.application.query.article.model.ArticleView;
import com.abservice.presentation.rest.article.response.ArticleListResponse;
import com.abservice.presentation.rest.article.response.ArticleResponse;
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
 * 記事集約の Query REST リソース
 *
 * <p>
 * 記事の詳細照会（GET）と一覧照会（GET、ページネーション付き）を受け付ける。未存在は例外ではなく
 * {@link GetArticleResult.NotFound} として扱い、404 を RFC 9457 Problem Details
 * （{@code application/problem+json}）で返す。
 * </p>
 */
@Path("/api/v1/articles")
public class ArticleQueryResource {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final GetArticleService getArticleService;
    private final ListArticlesService listArticlesService;

    /**
     * @param getArticleService
     *            記事詳細照会ユースケース
     * @param listArticlesService
     *            記事一覧照会ユースケース
     */
    public ArticleQueryResource(GetArticleService getArticleService, ListArticlesService listArticlesService) {
        this.getArticleService = getArticleService;
        this.listArticlesService = listArticlesService;
    }

    /**
     * 記事詳細を照会します。
     *
     * @param id
     *            記事のドメインID
     * @return 200 と記事詳細、未存在時は 404 の Problem Details
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") String id) {
        return getArticleService.query(new GetArticleQuery(id))
                .map(result -> toResponse(result, id));
    }

    private static Response toResponse(GetArticleResult result, String id) {
        return switch (result) {
            case GetArticleResult.Found(var article) -> Response.ok(toArticleResponse(article)).build();
            case GetArticleResult.NotFound() -> Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.valueOf(PROBLEM_JSON)).entity(notFoundProblem(id)).build();
        };
    }

    private static ProblemDetail notFoundProblem(String id) {
        return ProblemDetail.of(
                "ENTITY_NOT_FOUND",
                "Resource not found",
                Response.Status.NOT_FOUND.getStatusCode(),
                "Article not found: id=" + id,
                List.of());
    }

    private static ArticleResponse toArticleResponse(ArticleView view) {
        return new ArticleResponse(
                view.articleId(),
                view.articleType(),
                view.albumId(),
                view.title(),
                view.body(),
                view.bodyFormat(),
                view.introShort(),
                view.publishedAt(),
                view.updatedAtBusiness(),
                view.publicFlag());
    }

    /**
     * 記事一覧を照会します（ページネーション付き）。
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
        return listArticlesService.query(new ListArticlesQuery(page, size))
                .map(ArticleQueryResource::toListResponse);
    }

    private static Response toListResponse(ListArticlesResult result) {
        return Response.ok(
                new ArticleListResponse(
                        result.items().stream().map(ArticleQueryResource::toArticleResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }
}
