package com.abservice.presentation.rest.article;

import com.abservice.application.query.article.GetArticleResult;
import com.abservice.application.query.article.ListArticlesResult;
import com.abservice.application.query.article.model.ArticleView;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.presentation.rest.article.response.AdminAlbumArticleDetailResponse;
import com.abservice.presentation.rest.article.response.AdminArticleDetailResponse;
import com.abservice.presentation.rest.article.response.AdminArticleListResponse;
import com.abservice.presentation.rest.article.response.AdminArticleResponse;
import com.abservice.presentation.rest.article.response.AdminPlainArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicAlbumArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicAlbumArticleResponse;
import com.abservice.presentation.rest.article.response.PublicArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicArticleListResponse;
import com.abservice.presentation.rest.article.response.PublicArticleResponse;
import com.abservice.presentation.rest.article.response.PublicPlainArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicPlainArticleResponse;
import com.abservice.presentation.rest.exception.ProblemDetail;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 記事照会結果から HTTP 応答への変換
 *
 * <p>
 * 公開向け（{@link ArticleQueryResource}）と管理向け（{@link ArticleAdminQueryResource}）は
 * 対象範囲だけでなく応答表現も異なるため、要求元ごとに変換を持つ。未存在（404）の表現と Read Model の種別による
 * 振り分けは共通のため、本クラスに集約する。
 * </p>
 *
 * <p>
 * 詳細と一覧でも応答表現が異なる。詳細は本文を返し、一覧は記事を選ぶための表示に留める（`docs/DECISIONS.md` 20）。
 * </p>
 */
final class ArticleQueryResponses {

    private static final String PROBLEM_JSON = "application/problem+json";

    private ArticleQueryResponses() {
    }

    /**
     * 詳細照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会した記事のドメインID
     * @return 200 と記事詳細、未存在時は 404 の Problem Details
     */
    static Response toPublicResponse(GetArticleResult result, String id) {
        return toResponse(
                result,
                id,
                ArticleQueryResponses::toPublicArticleDetailResponse);
    }

    /**
     * 詳細照会結果を管理向けの応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会した記事のドメインID
     * @return 200 と記事詳細、未存在時は 404 の Problem Details
     */
    static Response toAdminResponse(GetArticleResult result, String id) {
        return toResponse(
                result,
                id,
                ArticleQueryResponses::toAdminArticleDetailResponse);
    }

    /**
     * 一覧照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 200 と記事一覧
     */
    static Response toPublicListResponse(ListArticlesResult result) {
        return Response.ok(
                new PublicArticleListResponse(
                        result.items().stream().map(ArticleQueryResponses::toPublicArticleResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }

    /**
     * 一覧照会結果を管理向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 200 と記事一覧
     */
    static Response toAdminListResponse(ListArticlesResult result) {
        return Response.ok(
                new AdminArticleListResponse(
                        result.items().stream().map(ArticleQueryResponses::toAdminArticleResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }

    private static <T> Response toResponse(
            GetArticleResult result,
            String id,
            Function<ArticleView, T> toDetailResponse) {
        return switch (result) {
            case GetArticleResult.Found(var article) -> Response.ok(toDetailResponse.apply(article)).build();
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

    /*
     * KEY-BY-TYPE: アルバム参照に関わる項目名は ALBUM 種別にしか現れない。値が無いことを表す null と、
     * 種別がその概念を持たないことを区別するため、キーの有無を種別で切り替える。
     */
    private static PublicArticleDetailResponse toPublicArticleDetailResponse(ArticleView view) {
        return switch (ArticleType.valueOf(view.articleType())) {
            case ALBUM -> new PublicAlbumArticleDetailResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.body(),
                    view.bodyFormat(),
                    publicPublishedAt(view),
                    view.albumId());
            case NOTE, NEWS, EVENT, OTHER -> new PublicPlainArticleDetailResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.body(),
                    view.bodyFormat(),
                    publicPublishedAt(view));
        };
    }

    private static PublicArticleResponse toPublicArticleResponse(ArticleView view) {
        return switch (ArticleType.valueOf(view.articleType())) {
            case ALBUM -> new PublicAlbumArticleResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.introShort(),
                    publicPublishedAt(view),
                    view.albumId());
            case NOTE, NEWS, EVENT, OTHER -> new PublicPlainArticleResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.introShort(),
                    publicPublishedAt(view));
        };
    }

    private static AdminArticleDetailResponse toAdminArticleDetailResponse(ArticleView view) {
        return switch (ArticleType.valueOf(view.articleType())) {
            case ALBUM -> new AdminAlbumArticleDetailResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.body(),
                    view.bodyFormat(),
                    view.introShort(),
                    view.publishedAt(),
                    view.updatedAtBusiness(),
                    view.publicFlag(),
                    view.albumId(),
                    view.formerAlbumId(),
                    view.albumReferenceLostAt(),
                    view.albumReferenceLostReason());
            case NOTE, NEWS, EVENT, OTHER -> new AdminPlainArticleDetailResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.body(),
                    view.bodyFormat(),
                    view.introShort(),
                    view.publishedAt(),
                    view.updatedAtBusiness(),
                    view.publicFlag());
        };
    }

    /*
     * CONTRACT: 公開向けの応答は公開日時を必ず持つ。共有の Read Model は下書きを含むため nullable だが、公開向けの
     * 照会は公開中のものだけを返すため、この境界で非nullへ絞る。破れていた場合に null を公開契約として返すのではなく、 ここで検出する。
     */
    private static Instant publicPublishedAt(ArticleView view) {
        return Objects.requireNonNull(
                view.publishedAt(),
                "公開向けの照会結果は公開中のものに限るため、publishedAt は値を持つ");
    }

    private static AdminArticleResponse toAdminArticleResponse(ArticleView view) {
        return new AdminArticleResponse(
                view.articleId(),
                view.articleType(),
                view.title(),
                view.publishedAt(),
                view.updatedAtBusiness(),
                view.publicFlag());
    }
}
