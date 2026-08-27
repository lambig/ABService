package com.abservice.presentation.rest.album;

import com.abservice.application.service.album.AddTrackInput;
import com.abservice.application.service.album.AddTrackOutput;
import com.abservice.application.service.album.AddTrackService;
import com.abservice.application.service.album.RemoveTrackInput;
import com.abservice.application.service.album.RemoveTrackService;
import com.abservice.application.service.album.ReorderTracksInput;
import com.abservice.application.service.album.ReorderTracksOutput;
import com.abservice.application.service.album.ReorderTracksService;
import com.abservice.application.service.album.UpdateTrackInput;
import com.abservice.application.service.album.UpdateTrackOutput;
import com.abservice.application.service.album.UpdateTrackService;
import com.abservice.presentation.rest.album.request.AddTrackRequest;
import com.abservice.presentation.rest.album.request.ReorderTracksRequest;
import com.abservice.presentation.rest.album.request.TrackTuneRequest;
import com.abservice.presentation.rest.album.request.UpdateTrackRequest;
import com.abservice.presentation.rest.album.response.AddTrackResponse;
import com.abservice.presentation.rest.album.response.ReorderTracksResponse;
import com.abservice.presentation.rest.album.response.ReorderTracksResponse.TrackOrderEntryResponse;
import com.abservice.presentation.rest.album.response.UpdateTrackResponse;
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

/**
 * アルバム集約内トラック（エンティティ）の Command REST リソース
 *
 * <p>
 * トラックはAlbum集約に属するエンティティであり独立した集約ではないため、常にアルバムのサブリソースとして
 * 操作する。追加（POST）・更新（PUT、全項目置換。
 * {@code tunes}はチューン専用の操作（#120の対象）で対象外）・削除（DELETE、対象トラックが
 * 存在しない場合は409。べき等ではない）・順序変更（PUT .../order）を受け付ける。検証・永続化はアプリケーション層に委譲し、
 * 検証失敗・対象不在・トラック番号重複は {@code DomainException} 経由で {@code DomainExceptionMapper}
 * が RFC 9457 Problem Details に変換する。全操作は管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/albums/{albumId}/tracks")
@RolesAllowed(SecurityRoles.ADMIN)
public class AlbumTrackCommandResource {

    private final AddTrackService addTrackService;
    private final UpdateTrackService updateTrackService;
    private final RemoveTrackService removeTrackService;
    private final ReorderTracksService reorderTracksService;

    /**
     * @param addTrackService
     *            トラック追加ユースケース
     * @param updateTrackService
     *            トラック更新ユースケース
     * @param removeTrackService
     *            トラック削除ユースケース
     * @param reorderTracksService
     *            トラック順序変更ユースケース
     */
    public AlbumTrackCommandResource(
            AddTrackService addTrackService,
            UpdateTrackService updateTrackService,
            RemoveTrackService removeTrackService,
            ReorderTracksService reorderTracksService) {
        this.addTrackService = addTrackService;
        this.updateTrackService = updateTrackService;
        this.removeTrackService = removeTrackService;
        this.reorderTracksService = reorderTracksService;
    }

    /**
     * アルバムにトラックを追加します。
     *
     * @param albumId
     *            追加先のアルバムID
     * @param request
     *            トラック追加リクエスト
     * @return 201 Created と追加結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> add(@PathParam("albumId") String albumId, AddTrackRequest request) {
        return addTrackService.execute(toInput(albumId, request))
                .map(AlbumTrackCommandResource::toCreated);
    }

    private static AddTrackInput toInput(String albumId, AddTrackRequest request) {
        return new AddTrackInput(
                albumId,
                request.trackNo(),
                request.title(),
                request.artistDisplayName(),
                request.artistSortKey(),
                TrackTuneRequest.toInputs(request.tunes()));
    }

    private static Response toCreated(AddTrackOutput output) {
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(output))
                .build();
    }

    private static AddTrackResponse toResponse(AddTrackOutput output) {
        return new AddTrackResponse(
                output.albumId(),
                output.trackId(),
                output.trackNo(),
                output.title());
    }

    /**
     * トラックを更新します（PUT風の全項目置換。{@code tunes}は対象外）。
     *
     * @param albumId
     *            更新対象トラックが属するアルバムID
     * @param trackId
     *            更新対象のトラックID
     * @param request
     *            トラック更新リクエスト
     * @return 200 OK と更新結果
     */
    @PUT
    @Path("/{trackId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> update(
            @PathParam("albumId") String albumId,
            @PathParam("trackId") String trackId,
            UpdateTrackRequest request) {
        return updateTrackService.execute(
                toInput(
                        albumId,
                        trackId,
                        request))
                .map(AlbumTrackCommandResource::toOk);
    }

    private static UpdateTrackInput toInput(
            String albumId,
            String trackId,
            UpdateTrackRequest request) {
        return new UpdateTrackInput(
                albumId,
                trackId,
                request.trackNo(),
                request.title(),
                request.artistDisplayName(),
                request.artistSortKey(),
                TrackTuneRequest.toInputs(request.tunes()));
    }

    private static Response toOk(UpdateTrackOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static UpdateTrackResponse toResponse(UpdateTrackOutput output) {
        return new UpdateTrackResponse(
                output.albumId(),
                output.trackId(),
                output.trackNo(),
                output.title());
    }

    /**
     * トラックを削除します。
     *
     * <p>
     * 対象トラックが存在しない場合はAlbum集約自身が {@code BusinessRuleViolationException}（409）で検証します
     * （削除対象アルバム集約自体の不在確認とは異なり、べき等な削除ではありません）。
     * </p>
     *
     * @param albumId
     *            削除対象トラックが属するアルバムID
     * @param trackId
     *            削除対象のトラックID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{trackId}")
    public Uni<Response> remove(@PathParam("albumId") String albumId, @PathParam("trackId") String trackId) {
        return removeTrackService.execute(new RemoveTrackInput(albumId, trackId))
                .replaceWith(Response.noContent().build());
    }

    /**
     * トラックの順序を変更します。
     *
     * @param albumId
     *            対象アルバムID
     * @param request
     *            トラック順序変更リクエスト
     * @return 200 OK と変更結果
     */
    @PUT
    @Path("/order")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> reorder(@PathParam("albumId") String albumId, ReorderTracksRequest request) {
        return reorderTracksService.execute(new ReorderTracksInput(albumId, request.orderedTrackIds()))
                .map(AlbumTrackCommandResource::toOk);
    }

    private static Response toOk(ReorderTracksOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static ReorderTracksResponse toResponse(ReorderTracksOutput output) {
        return new ReorderTracksResponse(
                output.albumId(),
                output.tracks().stream()
                        .map(
                                track -> new TrackOrderEntryResponse(
                                        track.trackId(),
                                        track.trackNo()))
                        .toList());
    }
}
