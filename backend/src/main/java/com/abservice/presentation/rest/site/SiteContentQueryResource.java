package com.abservice.presentation.rest.site;

import com.abservice.application.query.site.ListSiteContentsQuery;
import com.abservice.application.query.site.ListSiteContentsResult;
import com.abservice.application.query.site.ListSiteContentsService;
import com.abservice.application.query.site.model.SiteContentView;
import com.abservice.presentation.rest.site.response.SiteContentListResponse;
import com.abservice.presentation.rest.site.response.SiteContentResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * サイト文言の Query REST リソース
 *
 * <p>
 * サイト名・説明・トップの紹介文などの散文を全件返す。認証を要求しない。公開サイトはビルド時に、管理画面は
 * 編集フォームの初期表示にこれを使う。**管理向けの別経路は持たない**——返す項目が同じであり、要求元で
 * 変わらないため（`docs/DECISIONS.md` 20 の考え方）。
 * </p>
 *
 * <p>
 * 全件を1リクエストで返し、ページネーションを持たない。未登録のキーは応答に現れないため、利用側は該当する キーが無ければその区画を出さない。
 * </p>
 */
@Path("/api/v1/site-contents")
public class SiteContentQueryResource {

    private final ListSiteContentsService listSiteContentsService;

    /**
     * @param listSiteContentsService
     *            サイト文言の全件照会ユースケース
     */
    public SiteContentQueryResource(ListSiteContentsService listSiteContentsService) {
        this.listSiteContentsService = listSiteContentsService;
    }

    /**
     * サイト文言を全件照会します。
     *
     * @return サイト文言の一覧（キーの昇順）
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<SiteContentListResponse> list() {
        return listSiteContentsService.query(new ListSiteContentsQuery())
                .map(SiteContentQueryResource::toListResponse);
    }

    private static SiteContentListResponse toListResponse(ListSiteContentsResult result) {
        return new SiteContentListResponse(
                result.items().stream()
                        .map(SiteContentQueryResource::toResponse)
                        .toList());
    }

    private static SiteContentResponse toResponse(SiteContentView view) {
        return new SiteContentResponse(
                view.key(),
                view.content(),
                view.contentFormat());
    }
}
