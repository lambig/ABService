package com.abservice.presentation.rest.article;

import com.abservice.application.query.article.ListArticleTagsQuery;
import com.abservice.application.query.article.ListArticleTagsResult;
import com.abservice.application.query.article.ListArticleTagsService;
import com.abservice.presentation.rest.article.response.AdminArticleTagListResponse;
import com.abservice.presentation.rest.article.response.AdminArticleTagResponse;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * 記事タグの管理向け Query REST リソース
 *
 * <p>
 * 管理画面が記事にタグを付けるときの選択肢として、既存のタグ語彙を名前の昇順で返す。公開サイトはタグによる絞り込みを
 * 持たない（v1.0）ため、この照会は管理向けにだけ開く。管理者ロール（{@code Authorization: Bearer <APIキー>}）を
 * 要求する。
 * </p>
 */
@Path("/api/v1/admin/article-tags")
@RolesAllowed(SecurityRoles.ADMIN)
public class ArticleTagAdminQueryResource {

    private final ListArticleTagsService listArticleTagsService;

    /**
     * @param listArticleTagsService
     *            タグ一覧照会ユースケース
     */
    public ArticleTagAdminQueryResource(ListArticleTagsService listArticleTagsService) {
        this.listArticleTagsService = listArticleTagsService;
    }

    /**
     * タグ語彙の全件を名前の昇順で照会します。
     *
     * @return 200 とタグ一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list() {
        return listArticleTagsService.query(new ListArticleTagsQuery())
                .map(ArticleTagAdminQueryResource::toResponse);
    }

    private static Response toResponse(ListArticleTagsResult result) {
        return Response.ok(
                new AdminArticleTagListResponse(
                        result.items().stream()
                                .map(view -> new AdminArticleTagResponse(view.tagId(), view.name()))
                                .toList()))
                .build();
    }
}
