package com.abservice.presentation.rest.tune;

import com.abservice.application.service.tune.CreateTuneInput;
import com.abservice.application.service.tune.CreateTuneOutput;
import com.abservice.application.service.tune.CreateTuneService;
import com.abservice.presentation.rest.tune.request.CreateTuneRequest;
import com.abservice.presentation.rest.tune.response.CreateTuneResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * チューン集約の Command REST リソース
 *
 * <p>
 * チューンの作成（POST）を受け付ける。検証・永続化はアプリケーション層に委譲し、検証失敗は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。
 * </p>
 */
@Path("/api/v1/tunes")
public class TuneCommandResource {

    private final CreateTuneService createTuneService;

    /**
     * @param createTuneService
     *            チューン作成ユースケース
     */
    public TuneCommandResource(CreateTuneService createTuneService) {
        this.createTuneService = createTuneService;
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
}
