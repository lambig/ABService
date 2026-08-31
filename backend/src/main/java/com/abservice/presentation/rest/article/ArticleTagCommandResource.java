package com.abservice.presentation.rest.article;

import com.abservice.application.service.article.AddArticleTagInput;
import com.abservice.application.service.article.AddArticleTagOutput;
import com.abservice.application.service.article.AddArticleTagService;
import com.abservice.application.service.article.RemoveArticleTagInput;
import com.abservice.application.service.article.RemoveArticleTagService;
import com.abservice.presentation.rest.article.request.AddArticleTagRequest;
import com.abservice.presentation.rest.article.response.AddArticleTagResponse;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * 記事タグの Command REST リソース
 *
 * <p>
 * タグは複数の記事が共有する語彙だが、操作の対象は「その記事に付いたタグ」のため記事のサブリソースとして扱う。追加
 * （POST、名前で指定し既存があれば再利用）と削除（DELETE、付いていないタグを外す操作はべき等に成功する）を
 * 受け付ける。タグ語彙そのものは削除しない（他の記事が使っている場合があるため）。
 * </p>
 *
 * <p>
 * 検証失敗・対象不在・タグの重複は {@code DomainException} 経由で {@code DomainExceptionMapper} が
 * RFC 9457 Problem Details
 * に変換する。全操作は管理者ロール（{@code Authorization: Bearer <APIキー>}）を 要求する。
 * </p>
 */
@Path("/api/v1/articles/{articleId}/tags")
@RolesAllowed(SecurityRoles.ADMIN)
public class ArticleTagCommandResource {

    private final AddArticleTagService addArticleTagService;
    private final RemoveArticleTagService removeArticleTagService;

    /**
     * @param addArticleTagService
     *            タグ追加ユースケース
     * @param removeArticleTagService
     *            タグ削除ユースケース
     */
    public ArticleTagCommandResource(
            AddArticleTagService addArticleTagService,
            RemoveArticleTagService removeArticleTagService) {
        this.addArticleTagService = addArticleTagService;
        this.removeArticleTagService = removeArticleTagService;
    }

    /**
     * 記事にタグを付けます。
     *
     * @param articleId
     *            対象記事のID
     * @param request
     *            タグ追加リクエスト
     * @return 201 Created と付与結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> add(@PathParam("articleId") String articleId, AddArticleTagRequest request) {
        return addArticleTagService.execute(new AddArticleTagInput(articleId, request.name()))
                .map(ArticleTagCommandResource::toCreated);
    }

    /**
     * 記事からタグを外します。
     *
     * @param articleId
     *            対象記事のID
     * @param tagId
     *            外すタグのID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{tagId}")
    public Uni<Response> remove(@PathParam("articleId") String articleId, @PathParam("tagId") String tagId) {
        return removeArticleTagService.execute(new RemoveArticleTagInput(articleId, tagId))
                .replaceWith(Response.noContent().build());
    }

    private static Response toCreated(AddArticleTagOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(
                        new AddArticleTagResponse(
                                output.articleId(),
                                output.tagId(),
                                output.name()))
                .build();
    }
}
