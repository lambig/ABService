package com.abservice.presentation.rest.album;

import com.abservice.application.service.album.CreateAlbumInput;
import com.abservice.application.service.album.CreateAlbumInput.EventInput;
import com.abservice.application.service.album.CreateAlbumOutput;
import com.abservice.application.service.album.CreateAlbumService;
import com.abservice.application.service.album.DeleteAlbumInput;
import com.abservice.application.service.album.DeleteAlbumOutput;
import com.abservice.application.service.album.DeleteAlbumService;
import com.abservice.application.service.album.PublishAlbumInput;
import com.abservice.application.service.album.PublishAlbumOutput;
import com.abservice.application.service.album.PublishAlbumService;
import com.abservice.application.service.album.RegisterAlbumWithTracksInput;
import com.abservice.application.service.album.RegisterAlbumWithTracksOutput;
import com.abservice.application.service.album.RegisterAlbumWithTracksService;
import com.abservice.application.service.album.UnpublishAlbumInput;
import com.abservice.application.service.album.UnpublishAlbumOutput;
import com.abservice.application.service.album.UnpublishAlbumService;
import com.abservice.application.service.album.UpdateAlbumInput;
import com.abservice.application.service.album.UpdateAlbumOutput;
import com.abservice.application.service.album.UpdateAlbumService;
import com.abservice.presentation.rest.album.request.CreateAlbumRequest;
import com.abservice.presentation.rest.album.request.CreateAlbumRequest.EventRequest;
import com.abservice.presentation.rest.album.request.RegisterAlbumWithTracksRequest;
import com.abservice.presentation.rest.album.request.UpdateAlbumRequest;
import com.abservice.presentation.rest.album.response.CreateAlbumResponse;
import com.abservice.presentation.rest.album.response.DeleteAlbumResponse;
import com.abservice.presentation.rest.album.response.PublishAlbumResponse;
import com.abservice.presentation.rest.album.response.RegisterAlbumWithTracksResponse;
import com.abservice.presentation.rest.album.response.UnpublishAlbumResponse;
import com.abservice.presentation.rest.album.response.UpdateAlbumResponse;
import com.abservice.presentation.rest.security.SecurityRoles;
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
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * アルバム集約の Command REST リソース
 *
 * <p>
 * アルバムの作成（POST）・更新（PUT、全項目置換）・削除（DELETE、べき等）・公開（POST .../publish）・非公開化（POST
 * .../unpublish）・初期トラックを含めたワンリクエスト登録（POST .../with-tracks）を受け付ける。検証・永続化は
 * アプリケーション層に委譲し、検証失敗・対象不在は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。非公開化時、当該アルバムを
 * 参照する公開中の記事があれば連動して非公開化する（カスケード非公開）。全操作は管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/albums")
@RolesAllowed(SecurityRoles.ADMIN)
public class AlbumCommandResource {

    private final CreateAlbumService createAlbumService;
    private final UpdateAlbumService updateAlbumService;
    private final DeleteAlbumService deleteAlbumService;
    private final PublishAlbumService publishAlbumService;
    private final UnpublishAlbumService unpublishAlbumService;
    private final RegisterAlbumWithTracksService registerAlbumWithTracksService;

    /**
     * @param createAlbumService
     *            アルバム作成ユースケース
     * @param updateAlbumService
     *            アルバム更新ユースケース
     * @param deleteAlbumService
     *            アルバム削除ユースケース
     * @param publishAlbumService
     *            アルバム公開ユースケース
     * @param unpublishAlbumService
     *            アルバム非公開化ユースケース
     * @param registerAlbumWithTracksService
     *            アルバムと初期トラック一覧のワンリクエスト登録ユースケース
     */
    public AlbumCommandResource(
            CreateAlbumService createAlbumService,
            UpdateAlbumService updateAlbumService,
            DeleteAlbumService deleteAlbumService,
            PublishAlbumService publishAlbumService,
            UnpublishAlbumService unpublishAlbumService,
            RegisterAlbumWithTracksService registerAlbumWithTracksService) {
        this.createAlbumService = createAlbumService;
        this.updateAlbumService = updateAlbumService;
        this.deleteAlbumService = deleteAlbumService;
        this.publishAlbumService = publishAlbumService;
        this.unpublishAlbumService = unpublishAlbumService;
        this.registerAlbumWithTracksService = registerAlbumWithTracksService;
    }

    /**
     * アルバムを作成します。
     *
     * @param request
     *            アルバム作成リクエスト
     * @return 201 Created と作成結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> create(CreateAlbumRequest request) {
        return createAlbumService.execute(toInput(request))
                .map(AlbumCommandResource::toCreated);
    }

    private static CreateAlbumInput toInput(CreateAlbumRequest request) {
        return new CreateAlbumInput(
                request.title(),
                request.releaseDate(),
                request.artistDisplayName(),
                request.artistSortKey(),
                request.catalogNumber(),
                request.isdn(),
                request.coverImageKey(),
                request.description(),
                request.descriptionFormat(),
                toEventInput(request.event()));
    }

    private static @Nullable EventInput toEventInput(@Nullable EventRequest event) {
        return Optional.ofNullable(event)
                .map(
                        e -> new EventInput(
                                e.name(),
                                e.date(),
                                e.place(),
                                e.spaceNumber(),
                                e.note()))
                .orElse(null);
    }

    private static Response toCreated(CreateAlbumOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static CreateAlbumResponse toResponse(CreateAlbumOutput output) {
        return new CreateAlbumResponse(
                output.albumId(),
                output.title(),
                output.releaseDate(),
                output.artistDisplayName());
    }

    /**
     * アルバムを更新します（PUT風の全項目置換）。
     *
     * @param id
     *            更新対象のアルバムID
     * @param request
     *            アルバム更新リクエスト
     * @return 200 OK と更新結果
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("id") String id, UpdateAlbumRequest request) {
        return updateAlbumService.execute(toInput(id, request))
                .map(AlbumCommandResource::toOk);
    }

    private static UpdateAlbumInput toInput(String id, UpdateAlbumRequest request) {
        return new UpdateAlbumInput(
                id,
                request.title(),
                request.releaseDate(),
                request.artistDisplayName(),
                request.artistSortKey(),
                request.catalogNumber(),
                request.isdn(),
                request.coverImageKey(),
                request.description(),
                request.descriptionFormat(),
                toEventInput(request.event()));
    }

    private static UpdateAlbumInput.@Nullable EventInput toEventInput(
            UpdateAlbumRequest.@Nullable EventRequest event) {
        return Optional.ofNullable(event)
                .map(
                        e -> new UpdateAlbumInput.EventInput(
                                e.name(),
                                e.date(),
                                e.place(),
                                e.spaceNumber(),
                                e.note()))
                .orElse(null);
    }

    private static Response toOk(UpdateAlbumOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UpdateAlbumResponse toResponse(UpdateAlbumOutput output) {
        return new UpdateAlbumResponse(
                output.albumId(),
                output.title(),
                output.releaseDate(),
                output.artistDisplayName());
    }

    /**
     * アルバムを削除します（べき等。対象アルバムの存在有無を問わず200を返す）。
     *
     * <p>
     * 当該アルバムを参照していた記事は参照が失効し、公開中だったものは非公開へ戻ります。影響を受けた記事は応答に含めます。
     * </p>
     *
     * @param id
     *            削除対象のアルバムID
     * @return 200 OK と削除に伴う影響
     */
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> delete(@PathParam("id") String id) {
        return deleteAlbumService.execute(new DeleteAlbumInput(id))
                .map(AlbumCommandResource::toDeleteResponse);
    }

    private static Response toDeleteResponse(DeleteAlbumOutput output) {
        return Response.ok(
                new DeleteAlbumResponse(
                        output.affectedArticles().stream()
                                .map(AlbumCommandResource::toAffectedArticle)
                                .toList()))
                .build();
    }

    private static DeleteAlbumResponse.AffectedArticle toAffectedArticle(
            DeleteAlbumOutput.AffectedArticle affected) {
        return new DeleteAlbumResponse.AffectedArticle(
                affected.articleId(),
                affected.title(),
                affected.unpublished());
    }

    /**
     * アルバムを公開します。
     *
     * @param id
     *            公開対象のアルバムID
     * @return 200 OK と公開結果
     */
    @POST
    @Path("/{id}/publish")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> publish(@PathParam("id") String id) {
        return publishAlbumService.execute(new PublishAlbumInput(id))
                .map(AlbumCommandResource::toOk);
    }

    private static Response toOk(PublishAlbumOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static PublishAlbumResponse toResponse(PublishAlbumOutput output) {
        return new PublishAlbumResponse(
                output.albumId(),
                output.title(),
                output.published());
    }

    /**
     * アルバムを非公開化します。当該アルバムを参照する公開中の記事があれば連動して非公開化し、 結果に含めて返します（カスケード非公開）。
     *
     * @param id
     *            非公開化対象のアルバムID
     * @return 200 OK と非公開化結果
     */
    @POST
    @Path("/{id}/unpublish")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> unpublish(@PathParam("id") String id) {
        return unpublishAlbumService.execute(new UnpublishAlbumInput(id))
                .map(AlbumCommandResource::toOk);
    }

    private static Response toOk(UnpublishAlbumOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UnpublishAlbumResponse toResponse(UnpublishAlbumOutput output) {
        return new UnpublishAlbumResponse(
                output.albumId(),
                output.title(),
                output.published(),
                output.cascadeUnpublishedArticles().stream()
                        .map(AlbumCommandResource::toCascadeUnpublishedArticleResponse)
                        .toList());
    }

    private static UnpublishAlbumResponse.CascadeUnpublishedArticleResponse toCascadeUnpublishedArticleResponse(
            UnpublishAlbumOutput.CascadeUnpublishedArticle article) {
        return new UnpublishAlbumResponse.CascadeUnpublishedArticleResponse(
                article.articleId(),
                article.title());
    }

    /**
     * アルバムとその初期トラック一覧をワンリクエストで登録します。
     *
     * @param request
     *            アルバムと初期トラック一覧のワンリクエスト登録リクエスト
     * @return 201 Created と登録結果
     */
    @POST
    @Path("/with-tracks")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> registerWithTracks(RegisterAlbumWithTracksRequest request) {
        return registerAlbumWithTracksService.execute(toInput(request))
                .map(AlbumCommandResource::toCreated);
    }

    private static RegisterAlbumWithTracksInput toInput(RegisterAlbumWithTracksRequest request) {
        return new RegisterAlbumWithTracksInput(
                request.title(),
                request.releaseDate(),
                request.artistDisplayName(),
                request.artistSortKey(),
                request.catalogNumber(),
                request.isdn(),
                request.coverImageKey(),
                request.description(),
                request.descriptionFormat(),
                toEventInput(request.event()),
                toTrackInputs(request.tracks()));
    }

    private static RegisterAlbumWithTracksInput.@Nullable EventInput toEventInput(
            RegisterAlbumWithTracksRequest.@Nullable EventRequest event) {
        return Optional.ofNullable(event)
                .map(
                        e -> new RegisterAlbumWithTracksInput.EventInput(
                                e.name(),
                                e.date(),
                                e.place(),
                                e.spaceNumber(),
                                e.note()))
                .orElse(null);
    }

    private static @Nullable List<RegisterAlbumWithTracksInput.TrackInput> toTrackInputs(
            @Nullable List<RegisterAlbumWithTracksRequest.TrackRequest> tracks) {
        return Optional.ofNullable(tracks)
                .map(AlbumCommandResource::toTrackInputList)
                .orElse(null);
    }

    private static List<RegisterAlbumWithTracksInput.TrackInput> toTrackInputList(
            List<RegisterAlbumWithTracksRequest.TrackRequest> tracks) {
        return tracks.stream()
                .map(AlbumCommandResource::toTrackInput)
                .toList();
    }

    private static RegisterAlbumWithTracksInput.@Nullable TrackInput toTrackInput(
            RegisterAlbumWithTracksRequest.@Nullable TrackRequest track) {
        return Optional.ofNullable(track)
                .map(
                        t -> new RegisterAlbumWithTracksInput.TrackInput(
                                t.trackNo(),
                                t.title(),
                                t.artistDisplayName(),
                                t.artistSortKey()))
                .orElse(null);
    }

    private static Response toCreated(RegisterAlbumWithTracksOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static RegisterAlbumWithTracksResponse toResponse(RegisterAlbumWithTracksOutput output) {
        return new RegisterAlbumWithTracksResponse(
                output.albumId(),
                output.title(),
                output.releaseDate(),
                output.artistDisplayName(),
                output.tracks().stream()
                        .map(
                                track -> new RegisterAlbumWithTracksResponse.TrackSummaryResponse(
                                        track.trackId(),
                                        track.trackNo(),
                                        track.title()))
                        .toList());
    }
}
