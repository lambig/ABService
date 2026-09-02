package com.abservice.presentation.rest.article;

import com.abservice.application.query.article.GetArticleResult;
import com.abservice.application.query.article.ListArticlesResult;
import com.abservice.application.query.article.model.ArticleTagView;
import com.abservice.application.query.article.model.ArticleView;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.presentation.rest.article.response.AdminAlbumArticleDetailResponse;
import com.abservice.presentation.rest.article.response.AdminArticleDetailResponse;
import com.abservice.presentation.rest.article.response.AdminArticleListResponse;
import com.abservice.presentation.rest.article.response.AdminArticleResponse;
import com.abservice.presentation.rest.article.response.AdminArticleTagResponse;
import com.abservice.presentation.rest.article.response.AdminPlainArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicAlbumArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicAlbumArticleResponse;
import com.abservice.presentation.rest.article.response.PublicArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicArticleListResponse;
import com.abservice.presentation.rest.article.response.PublicArticleResponse;
import com.abservice.presentation.rest.article.response.PublicPlainArticleDetailResponse;
import com.abservice.presentation.rest.article.response.PublicPlainArticleResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 記事照会結果から HTTP 応答への変換
 *
 * <p>
 * 公開向け（{@link ArticleQueryResource}）と管理向け（{@link ArticleAdminQueryResource}）は
 * 対象範囲だけでなく応答表現も異なるため、要求元ごとに変換を持つ。未存在の扱いと Read Model の種別による
 * 振り分けは共通のため、本クラスに集約する。
 * </p>
 *
 * <p>
 * 未存在は {@link EntityNotFoundException} を投げ、HTTP への変換は
 * {@code presentation.rest.exception.DomainExceptionMapper}
 * に委ねる。応答本体の型を返すことで、API 定義の レスポンススキーマが実装から生成される。
 * </p>
 *
 * <p>
 * 詳細と一覧でも応答表現が異なる。詳細は本文を返し、一覧は記事を選ぶための表示に留める（`docs/DECISIONS.md` 20）。
 * </p>
 */
final class ArticleQueryResponses {

    private static final String ENTITY_NAME = "Article";

    private ArticleQueryResponses() {
    }

    /**
     * 詳細照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会した記事のドメインID
     * @return 記事詳細
     * @throws EntityNotFoundException
     *             公開中の記事が存在しない場合
     */
    static PublicArticleDetailResponse toPublicResponse(GetArticleResult result, String id) {
        return toDetail(
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
     * @return 記事詳細
     * @throws EntityNotFoundException
     *             記事が存在しない場合
     */
    static AdminArticleDetailResponse toAdminResponse(GetArticleResult result, String id) {
        return toDetail(
                result,
                id,
                ArticleQueryResponses::toAdminArticleDetailResponse);
    }

    /**
     * 一覧照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 記事一覧
     */
    static PublicArticleListResponse toPublicListResponse(ListArticlesResult result) {
        return new PublicArticleListResponse(
                result.items().stream().map(ArticleQueryResponses::toPublicArticleResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    /**
     * 一覧照会結果を管理向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 記事一覧
     */
    static AdminArticleListResponse toAdminListResponse(ListArticlesResult result) {
        return new AdminArticleListResponse(
                result.items().stream().map(ArticleQueryResponses::toAdminArticleResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private static <T> T toDetail(
            GetArticleResult result,
            String id,
            Function<ArticleView, T> toDetailResponse) {
        return switch (result) {
            case GetArticleResult.Found(var article) -> toDetailResponse.apply(article);
            case GetArticleResult.NotFound() -> throw EntityNotFoundException.of(ENTITY_NAME, id);
        };
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
                    view.albumId(),
                    toTagNames(view));
            case NOTE, NEWS, EVENT, OTHER -> new PublicPlainArticleDetailResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.body(),
                    view.bodyFormat(),
                    publicPublishedAt(view),
                    toTagNames(view));
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
                    view.albumReferenceLostReason(),
                    toTagResponses(view));
            case NOTE, NEWS, EVENT, OTHER -> new AdminPlainArticleDetailResponse(
                    view.articleId(),
                    view.articleType(),
                    view.title(),
                    view.body(),
                    view.bodyFormat(),
                    view.introShort(),
                    view.publishedAt(),
                    view.updatedAtBusiness(),
                    view.publicFlag(),
                    toTagResponses(view));
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

    private static List<String> toTagNames(ArticleView view) {
        return view.tags().stream()
                .map(ArticleTagView::name)
                .toList();
    }

    private static List<AdminArticleTagResponse> toTagResponses(ArticleView view) {
        return view.tags().stream()
                .map(tag -> new AdminArticleTagResponse(tag.tagId(), tag.name()))
                .toList();
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
