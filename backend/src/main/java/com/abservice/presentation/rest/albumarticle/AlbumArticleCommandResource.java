package com.abservice.presentation.rest.albumarticle;

import com.abservice.application.service.albumarticle.CreateAlbumArticleInput;
import com.abservice.application.service.albumarticle.CreateAlbumArticleInput.DistributionInput;
import com.abservice.application.service.albumarticle.CreateAlbumArticleOutput;
import com.abservice.application.service.albumarticle.CreateAlbumArticleService;
import com.abservice.presentation.rest.albumarticle.request.CreateAlbumArticleRequest;
import com.abservice.presentation.rest.albumarticle.request.CreateAlbumArticleRequest.DistributionRequest;
import com.abservice.presentation.rest.albumarticle.response.CreateAlbumArticleResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事集約の Command REST リソース
 *
 * <p>
 * アルバム記事の作成（POST）を受け付ける。検証・永続化はアプリケーション層に委譲し、検証失敗は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。
 * </p>
 */
@Path("/api/v1/album-articles")
public class AlbumArticleCommandResource {

    private final CreateAlbumArticleService createAlbumArticleService;

    /**
     * @param createAlbumArticleService
     *            アルバム記事作成ユースケース
     */
    public AlbumArticleCommandResource(CreateAlbumArticleService createAlbumArticleService) {
        this.createAlbumArticleService = createAlbumArticleService;
    }

    /**
     * アルバム記事を作成します。
     *
     * @param request
     *            アルバム記事作成リクエスト
     * @return 201 Created と作成結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> create(CreateAlbumArticleRequest request) {
        return createAlbumArticleService.execute(toInput(request))
                .map(AlbumArticleCommandResource::toCreated);
    }

    private static CreateAlbumArticleInput toInput(CreateAlbumArticleRequest request) {
        return new CreateAlbumArticleInput(
                request.albumId(),
                request.introLong(),
                request.introShort(),
                request.firstEventSpace(),
                request.labelTag(),
                toDistributionInput(request.distribution()));
    }

    private static @Nullable DistributionInput toDistributionInput(@Nullable DistributionRequest distribution) {
        return Optional.ofNullable(distribution)
                .map(
                        d -> new DistributionInput(
                                d.physicalPrice(),
                                d.downloadPrice(),
                                d.demoUrl(),
                                d.note()))
                .orElse(null);
    }

    private static Response toCreated(CreateAlbumArticleOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static CreateAlbumArticleResponse toResponse(CreateAlbumArticleOutput output) {
        return new CreateAlbumArticleResponse(
                output.albumId(),
                output.introShort(),
                output.labelTag());
    }
}
