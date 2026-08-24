package com.abservice.presentation.rest.tune;

import com.abservice.application.query.tune.GetTuneQuery;
import com.abservice.application.query.tune.GetTuneResult;
import com.abservice.application.query.tune.GetTuneService;
import com.abservice.application.query.tune.ListTunesQuery;
import com.abservice.application.query.tune.ListTunesResult;
import com.abservice.application.query.tune.ListTunesService;
import com.abservice.application.query.tune.model.TuneView;
import com.abservice.presentation.rest.exception.ProblemDetail;
import com.abservice.presentation.rest.security.SecurityRoles;
import com.abservice.presentation.rest.tune.response.TuneListResponse;
import com.abservice.presentation.rest.tune.response.TuneResponse;
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
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * チューン集約の Query REST リソース
 *
 * <p>
 * チューンの詳細照会（GET）と一覧照会（GET、ページネーション付き）を受け付ける。未存在は例外ではなく
 * {@link GetTuneResult.NotFound} として扱い、404 を RFC 9457 Problem Details
 * （{@code application/problem+json}）で返す。チューンは公開サイトが直接参照しない管理用マスタのため、参照も管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/tunes")
@RolesAllowed(SecurityRoles.ADMIN)
public class TuneQueryResource {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final GetTuneService getTuneService;
    private final ListTunesService listTunesService;

    /**
     * @param getTuneService
     *            チューン詳細照会ユースケース
     * @param listTunesService
     *            チューン一覧照会ユースケース
     */
    public TuneQueryResource(GetTuneService getTuneService, ListTunesService listTunesService) {
        this.getTuneService = getTuneService;
        this.listTunesService = listTunesService;
    }

    /**
     * チューン詳細を照会します。
     *
     * @param id
     *            チューンのドメインID
     * @return 200 とチューン詳細、未存在時は 404 の Problem Details
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") String id) {
        return getTuneService.query(new GetTuneQuery(id))
                .map(result -> toResponse(result, id));
    }

    private static Response toResponse(GetTuneResult result, String id) {
        return switch (result) {
            case GetTuneResult.Found(var tune) -> Response.ok(toTuneResponse(tune)).build();
            case GetTuneResult.NotFound() -> Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.valueOf(PROBLEM_JSON)).entity(notFoundProblem(id)).build();
        };
    }

    private static ProblemDetail notFoundProblem(String id) {
        return ProblemDetail.of(
                "ENTITY_NOT_FOUND",
                "Resource not found",
                Response.Status.NOT_FOUND.getStatusCode(),
                "Tune not found: id=" + id,
                List.of());
    }

    private static TuneResponse toTuneResponse(TuneView view) {
        return new TuneResponse(
                view.tuneId(),
                view.title(),
                view.tuneKind(),
                view.defaultComposerCredit(),
                view.defaultArrangerCredit(),
                view.originalWorkTitle(),
                view.originalWorkCredit(),
                view.tuneType(),
                view.defaultKey(),
                view.defaultTempo());
    }

    /**
     * チューン一覧を照会します（ページネーション付き）。
     *
     * @param page
     *            ページ番号（0始まり。デフォルト0）
     * @param size
     *            1ページの件数（デフォルト20、最大100）
     * @param sort
     *            並び順のキー（未指定なら登録の新しい順）
     * @param direction
     *            並び順の向き（未指定ならキーごとの既定）
     * @return 200 とチューン一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") @Nullable String sort,
            @QueryParam("direction") @Nullable String direction) {
        return listTunesService.query(
                new ListTunesQuery(
                        page,
                        size,
                        sort,
                        direction))
                .map(TuneQueryResource::toListResponse);
    }

    private static Response toListResponse(ListTunesResult result) {
        return Response.ok(
                new TuneListResponse(
                        result.items().stream().map(TuneQueryResource::toTuneResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }
}
