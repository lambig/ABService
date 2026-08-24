package com.abservice.presentation.rest.article;

import com.abservice.application.query.article.GetArticleResult;
import com.abservice.application.query.article.ListArticlesResult;
import com.abservice.application.query.article.model.ArticleView;
import com.abservice.presentation.rest.article.response.ArticleListResponse;
import com.abservice.presentation.rest.article.response.ArticleResponse;
import com.abservice.presentation.rest.exception.ProblemDetail;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * 記事照会結果から HTTP 応答への変換
 *
 * <p>
 * 公開向け（{@link ArticleQueryResource}）と管理向け（{@link ArticleAdminQueryResource}）は対象範囲だけが
 * 異なり応答表現は同一のため、変換は本クラスに集約する。
 * </p>
 */
final class ArticleQueryResponses {

    private static final String PROBLEM_JSON = "application/problem+json";

    private ArticleQueryResponses() {
    }

    /**
     * 詳細照会結果を応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会した記事のドメインID
     * @return 200 と記事詳細、未存在時は 404 の Problem Details
     */
    static Response toResponse(GetArticleResult result, String id) {
        return switch (result) {
            case GetArticleResult.Found(var article) -> Response.ok(toArticleResponse(article)).build();
            case GetArticleResult.NotFound() -> Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.valueOf(PROBLEM_JSON)).entity(notFoundProblem(id)).build();
        };
    }

    /**
     * 一覧照会結果を応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 200 と記事一覧
     */
    static Response toListResponse(ListArticlesResult result) {
        return Response.ok(
                new ArticleListResponse(
                        result.items().stream().map(ArticleQueryResponses::toArticleResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
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
                view.publicFlag(),
                view.formerAlbumId(),
                view.albumReferenceLostAt(),
                view.albumReferenceLostReason());
    }
}
