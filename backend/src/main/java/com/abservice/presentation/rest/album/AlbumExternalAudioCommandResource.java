package com.abservice.presentation.rest.album;

import com.abservice.application.service.album.AddExternalAudioInput;
import com.abservice.application.service.album.AddExternalAudioOutput;
import com.abservice.application.service.album.AddExternalAudioService;
import com.abservice.application.service.album.RemoveExternalAudioInput;
import com.abservice.application.service.album.RemoveExternalAudioService;
import com.abservice.application.service.album.ReorderExternalAudiosInput;
import com.abservice.application.service.album.ReorderExternalAudiosOutput;
import com.abservice.application.service.album.ReorderExternalAudiosService;
import com.abservice.presentation.rest.album.request.AddExternalAudioRequest;
import com.abservice.presentation.rest.album.request.ReorderExternalAudiosRequest;
import com.abservice.presentation.rest.album.response.AddExternalAudioResponse;
import com.abservice.presentation.rest.album.response.ReorderExternalAudiosResponse;
import com.abservice.presentation.rest.album.response.ReorderExternalAudiosResponse.ExternalAudioOrderEntryResponse;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * アルバム集約内の外部音源（エンティティ）の Command REST リソース
 *
 * <p>
 * 外部音源はAlbum集約に属するエンティティであり独立した集約ではないため、常にアルバムのサブリソースとして操作する。
 * 追加（POST）・削除（DELETE、対象が存在しない場合は409。べき等ではない）・表示順変更（PUT .../order）を受け付ける。
 * 個別の更新（PUT）は持たない（保持するのはURLだけで、URLの差し替えは削除と追加で表現できる）。埋め込み可能な
 * ホストかどうかの検証はドメイン層の値オブジェクトが担い、検証失敗・対象不在・URL重複は {@code DomainException} 経由で
 * {@code DomainExceptionMapper} が RFC 9457 Problem Details に変換する。全操作は管理者ロール
 * （{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/albums/{albumId}/external-audios")
@RolesAllowed(SecurityRoles.ADMIN)
public class AlbumExternalAudioCommandResource {

    private final AddExternalAudioService addExternalAudioService;
    private final RemoveExternalAudioService removeExternalAudioService;
    private final ReorderExternalAudiosService reorderExternalAudiosService;

    /**
     * @param addExternalAudioService
     *            外部音源追加ユースケース
     * @param removeExternalAudioService
     *            外部音源削除ユースケース
     * @param reorderExternalAudiosService
     *            外部音源順序変更ユースケース
     */
    public AlbumExternalAudioCommandResource(
            AddExternalAudioService addExternalAudioService,
            RemoveExternalAudioService removeExternalAudioService,
            ReorderExternalAudiosService reorderExternalAudiosService) {
        this.addExternalAudioService = addExternalAudioService;
        this.removeExternalAudioService = removeExternalAudioService;
        this.reorderExternalAudiosService = reorderExternalAudiosService;
    }

    /**
     * アルバムに外部音源を追加します。
     *
     * @param albumId
     *            追加先のアルバムID
     * @param request
     *            外部音源追加リクエスト
     * @return 201 Created と追加結果
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(RestResponse.StatusCode.CREATED)
    public Uni<AddExternalAudioResponse> add(
            @PathParam("albumId") String albumId,
            AddExternalAudioRequest request) {
        return addExternalAudioService.execute(new AddExternalAudioInput(albumId, request.url()))
                .map(AlbumExternalAudioCommandResource::toResponse);
    }

    private static AddExternalAudioResponse toResponse(AddExternalAudioOutput output) {
        return new AddExternalAudioResponse(
                output.albumId(),
                output.externalAudioId(),
                output.displayOrder(),
                output.url());
    }

    /**
     * 外部音源を削除します。
     *
     * <p>
     * 対象が存在しない場合はAlbum集約自身が {@code BusinessRuleViolationException}（409）で検証します
     * （削除対象アルバム集約自体の不在確認とは異なり、べき等な削除ではありません）。残る外部音源の表示順は詰め直されます。
     * </p>
     *
     * @param albumId
     *            削除対象の外部音源が属するアルバムID
     * @param externalAudioId
     *            削除対象の外部音源ID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{externalAudioId}")
    public Uni<Void> remove(
            @PathParam("albumId") String albumId,
            @PathParam("externalAudioId") String externalAudioId) {
        return removeExternalAudioService.execute(new RemoveExternalAudioInput(albumId, externalAudioId))
                .replaceWithVoid();
    }

    /**
     * 外部音源の表示順を変更します。
     *
     * @param albumId
     *            対象アルバムID
     * @param request
     *            外部音源順序変更リクエスト
     * @return 200 OK と変更結果
     */
    @PUT
    @Path("/order")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<ReorderExternalAudiosResponse> reorder(
            @PathParam("albumId") String albumId,
            ReorderExternalAudiosRequest request) {
        return reorderExternalAudiosService
                .execute(new ReorderExternalAudiosInput(albumId, request.orderedExternalAudioIds()))
                .map(AlbumExternalAudioCommandResource::toResponse);
    }

    private static ReorderExternalAudiosResponse toResponse(ReorderExternalAudiosOutput output) {
        return new ReorderExternalAudiosResponse(
                output.albumId(),
                output.externalAudios().stream()
                        .map(
                                audio -> new ExternalAudioOrderEntryResponse(
                                        audio.externalAudioId(),
                                        audio.displayOrder()))
                        .toList());
    }
}
