package com.abservice.presentation.rest.article;

import com.abservice.application.query.Audience;
import com.abservice.application.query.article.GetArticleQuery;
import com.abservice.application.query.article.GetArticleResult;
import com.abservice.application.query.article.GetArticleService;
import com.abservice.application.query.article.ListArticlesQuery;
import com.abservice.application.query.article.ListArticlesService;
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
 * 記事集約の公開向け Query REST リソース
 *
 * <p>
 * 記事の詳細照会（GET）と一覧照会（GET、ページネーション付き）を認証不要で受け付ける。公開中の記事のみを対象とし、下書きは
 * 未存在として扱う（下書きを含む照会は {@link ArticleAdminQueryResource}）。応答は公開サイトが使う項目だけを持ち、
 * 公開側で起こり得ないこと（下書き・参照の失効）のための項目名は出さない。未存在は例外ではなく
 * {@link GetArticleResult.NotFound} として扱い、404 を RFC 9457 Problem Details
 * （{@code application/problem+json}）で返す。
 * </p>
 */
@Path("/api/v1/articles")
public class ArticleQueryResource {

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
     * 公開中の記事詳細を照会します。
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
                        Audience.PUBLIC))
                .map(result -> ArticleQueryResponses.toPublicResponse(result, id));
    }

    /**
     * 公開中の記事一覧を照会します（ページネーション付き）。
     *
     * @param page
     *            ページ番号（0始まり。デフォルト0）
     * @param size
     *            1ページの件数（デフォルト20、最大100）
     * @param sort
     *            並び順のキー（未指定なら登録の新しい順）
     * @param direction
     *            並び順の向き（未指定ならキーごとの既定）
     * @return 200 と記事一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") @Nullable String sort,
            @QueryParam("direction") @Nullable String direction) {
        return listArticlesService.query(
                new ListArticlesQuery(
                        page,
                        size,
                        Audience.PUBLIC,
                        sort,
                        direction))
                .map(ArticleQueryResponses::toPublicListResponse);
    }
}
