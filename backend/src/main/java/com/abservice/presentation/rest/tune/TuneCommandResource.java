package com.abservice.presentation.rest.tune;

import com.abservice.application.service.tune.CreateTuneInput;
import com.abservice.application.service.tune.CreateTuneOutput;
import com.abservice.application.service.tune.CreateTuneService;
import com.abservice.application.service.tune.DeleteTuneInput;
import com.abservice.application.service.tune.DeleteTuneService;
import com.abservice.application.service.tune.UpdateTuneInput;
import com.abservice.application.service.tune.UpdateTuneOutput;
import com.abservice.application.service.tune.UpdateTuneService;
import com.abservice.presentation.rest.tune.request.CreateTuneRequest;
import com.abservice.presentation.rest.tune.request.UpdateTuneRequest;
import com.abservice.presentation.rest.tune.response.CreateTuneResponse;
import com.abservice.presentation.rest.security.SecurityRoles;
import com.abservice.presentation.rest.tune.response.UpdateTuneResponse;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * チューン集約の Command REST リソース
 *
 * <p>
 * チューンの作成（POST）・更新（PUT、全項目置換）・削除（DELETE、べき等）を受け付ける。検証・永続化は
 * アプリケーション層に委譲し、検証失敗・対象不在は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。全操作は管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/tunes")
@RolesAllowed(SecurityRoles.ADMIN)
public class TuneCommandResource {

    private final CreateTuneService createTuneService;
    private final UpdateTuneService updateTuneService;
    private final DeleteTuneService deleteTuneService;

    /**
     * @param createTuneService
     *            チューン作成ユースケース
     * @param updateTuneService
     *            チューン更新ユースケース
     * @param deleteTuneService
     *            チューン削除ユースケース
     */
    public TuneCommandResource(
            CreateTuneService createTuneService,
            UpdateTuneService updateTuneService,
            DeleteTuneService deleteTuneService) {
        this.createTuneService = createTuneService;
        this.updateTuneService = updateTuneService;
        this.deleteTuneService = deleteTuneService;
    }

    /**
     * チューンを作成します。
     *
     * @param request
     *            チューン作成リクエスト
     * @return 201 Created と作成結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> create(CreateTuneRequest request) {
        return createTuneService.execute(toInput(request))
                .map(TuneCommandResource::toCreated);
    }

    private static CreateTuneInput toInput(CreateTuneRequest request) {
        return new CreateTuneInput(
                request.title(),
                request.tuneKind(),
                request.defaultComposerCredit(),
                request.defaultArrangerCredit(),
                request.originalWorkTitle(),
                request.originalWorkCredit(),
                request.tuneType(),
                request.defaultKey(),
                request.defaultTempo());
    }

    private static Response toCreated(CreateTuneOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static CreateTuneResponse toResponse(CreateTuneOutput output) {
        return new CreateTuneResponse(
                output.tuneId(),
                output.title(),
                output.tuneKind());
    }

    /**
     * チューンを更新します（PUT風の全項目置換）。
     *
     * @param id
     *            更新対象のチューンID
     * @param request
     *            チューン更新リクエスト
     * @return 200 OK と更新結果
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("id") String id, UpdateTuneRequest request) {
        return updateTuneService.execute(toInput(id, request))
                .map(TuneCommandResource::toOk);
    }

    private static UpdateTuneInput toInput(String id, UpdateTuneRequest request) {
        return new UpdateTuneInput(
                id,
                request.title(),
                request.tuneKind(),
                request.defaultComposerCredit(),
                request.defaultArrangerCredit(),
                request.originalWorkTitle(),
                request.originalWorkCredit(),
                request.tuneType(),
                request.defaultKey(),
                request.defaultTempo());
    }

    private static Response toOk(UpdateTuneOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UpdateTuneResponse toResponse(UpdateTuneOutput output) {
        return new UpdateTuneResponse(
                output.tuneId(),
                output.title(),
                output.tuneKind());
    }

    /**
     * チューンを削除します（べき等。対象チューンの存在有無を問わず204を返す）。
     *
     * @param id
     *            削除対象のチューンID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") String id) {
        return deleteTuneService.execute(new DeleteTuneInput(id))
                .replaceWith(Response.noContent().build());
    }
}
