package com.abservice.presentation.rest.tune;

import com.abservice.application.query.tune.GetTuneQuery;
import com.abservice.application.query.tune.GetTuneResult;
import com.abservice.application.query.tune.GetTuneService;
import com.abservice.application.query.tune.ListTunesQuery;
import com.abservice.application.query.tune.ListTunesResult;
import com.abservice.application.query.tune.ListTunesService;
import com.abservice.application.query.tune.model.TuneView;
import com.abservice.domain.exception.EntityNotFoundException;
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
import org.jspecify.annotations.Nullable;

/**
 * チューン集約の Query REST リソース
 *
 * <p>
 * チューンの詳細照会（GET）と一覧照会（GET、ページネーション付き）を受け付ける。照会結果の
 * {@link GetTuneResult.NotFound} は {@link EntityNotFoundException} へ変換し、404 を
 * RFC 9457 Problem Details
 * （{@code application/problem+json}）で返す。チューンは公開サイトが直接参照しない管理用マスタのため、
 * 参照も管理者ロール（{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/tunes")
@RolesAllowed(SecurityRoles.ADMIN)
public class TuneQueryResource {

    private static final String ENTITY_NAME = "Tune";

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
     * @return チューン詳細（未存在時は 404 の Problem Details）
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<TuneResponse> get(@PathParam("id") String id) {
        return getTuneService.query(new GetTuneQuery(id))
                .map(result -> toDetail(result, id));
    }

    private static TuneResponse toDetail(GetTuneResult result, String id) {
        return switch (result) {
            case GetTuneResult.Found(var tune) -> toTuneResponse(tune);
            case GetTuneResult.NotFound() -> throw EntityNotFoundException.of(ENTITY_NAME, id);
        };
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
     * @return チューン一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<TuneListResponse> list(
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

    private static TuneListResponse toListResponse(ListTunesResult result) {
        return new TuneListResponse(
                result.items().stream().map(TuneQueryResource::toTuneResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
