package com.abservice.presentation.rest.article;

import com.abservice.application.query.article.GetArticleQuery;
import com.abservice.application.query.article.GetArticleResult;
import com.abservice.application.query.article.GetArticleService;
import com.abservice.application.query.article.model.ArticleView;
import com.abservice.presentation.rest.article.response.ArticleResponse;
import com.abservice.presentation.rest.exception.ProblemDetail;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * 記事集約の Query REST リソース
 *
 * <p>
 * 記事の詳細照会（GET）を受け付ける。未存在は例外ではなく {@link GetArticleResult.NotFound} として扱い、 404 を
 * RFC 9457 Problem Details（{@code application/problem+json}）で返す。
 * </p>
 */
@Path("/api/v1/articles")
public class ArticleQueryResource {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final GetArticleService getArticleService;

    /**
     * @param getArticleService
     *            記事詳細照会ユースケース
     */
    public ArticleQueryResource(GetArticleService getArticleService) {
        this.getArticleService = getArticleService;
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
        return getArticleService.query(new GetArticleQuery(id)).map(result -> toResponse(result, id));
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
}
