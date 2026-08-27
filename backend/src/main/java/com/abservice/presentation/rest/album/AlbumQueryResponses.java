package com.abservice.presentation.rest.album;

import com.abservice.application.query.album.GetAlbumResult;
import com.abservice.application.query.album.ListAlbumsResult;
import com.abservice.application.query.album.model.AlbumView;
import com.abservice.presentation.rest.album.response.AlbumDetailResponse;
import com.abservice.presentation.rest.album.response.AlbumDetailResponse.TrackResponse;
import com.abservice.presentation.rest.album.response.AlbumDetailResponse.TrackTuneResponse;
import com.abservice.presentation.rest.album.response.AlbumListResponse;
import com.abservice.presentation.rest.album.response.AlbumResponse;
import com.abservice.presentation.rest.album.response.AlbumResponse.ExternalAudioResponse;
import com.abservice.presentation.rest.exception.ProblemDetail;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * アルバム照会結果から HTTP 応答への変換
 *
 * <p>
 * 公開向け（{@link AlbumQueryResource}）と管理向け（{@link AlbumAdminQueryResource}）は対象範囲だけが
 * 異なり応答表現は同一のため、変換は本クラスに集約する。
 * </p>
 *
 * <p>
 * 詳細と一覧では応答表現が異なる。詳細は曲目を持つ（{@link AlbumDetailResponse}）が、一覧は作品を選ぶための
 * 表示に留めるため曲目を返さない（{@link AlbumResponse}）。
 * </p>
 */
final class AlbumQueryResponses {

    private static final String PROBLEM_JSON = "application/problem+json";

    private AlbumQueryResponses() {
    }

    /**
     * 詳細照会結果を応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会したアルバムのドメインID
     * @return 200 とアルバム詳細、未存在時は 404 の Problem Details
     */
    static Response toResponse(GetAlbumResult result, String id) {
        return switch (result) {
            case GetAlbumResult.Found(var album) -> Response.ok(toAlbumDetailResponse(album)).build();
            case GetAlbumResult.NotFound() -> Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.valueOf(PROBLEM_JSON)).entity(notFoundProblem(id)).build();
        };
    }

    /**
     * 一覧照会結果を応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 200 とアルバム一覧
     */
    static Response toListResponse(ListAlbumsResult result) {
        return Response.ok(
                new AlbumListResponse(
                        result.items().stream().map(AlbumQueryResponses::toAlbumResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }

    private static ProblemDetail notFoundProblem(String id) {
        return ProblemDetail.of(
                "ENTITY_NOT_FOUND",
                "Resource not found",
                Response.Status.NOT_FOUND.getStatusCode(),
                "Album not found: id=" + id,
                List.of());
    }

    private static AlbumDetailResponse toAlbumDetailResponse(AlbumView view) {
        return new AlbumDetailResponse(
                view.albumId(),
                view.title(),
                view.releaseDate(),
                view.artistDisplayName(),
                view.artistSortKey(),
                view.description(),
                view.descriptionFormat(),
                view.catalogNumber(),
                view.isdn(),
                view.eventName(),
                view.eventDate(),
                view.eventPlace(),
                view.eventSpaceNumber(),
                view.eventNote(),
                view.publishedAt(),
                view.coverImageUrl(),
                toExternalAudioResponses(view),
                toTrackResponses(view));
    }

    private static List<TrackResponse> toTrackResponses(AlbumView view) {
        return view.tracks().stream()
                .map(AlbumQueryResponses::toTrackResponse)
                .toList();
    }

    private static TrackResponse toTrackResponse(AlbumView.TrackView track) {
        return new TrackResponse(
                track.trackId(),
                track.trackNo(),
                track.title(),
                track.artistDisplayName(),
                track.artistSortKey(),
                toTrackTuneResponses(track));
    }

    private static List<TrackTuneResponse> toTrackTuneResponses(AlbumView.TrackView track) {
        return track.tunes().stream()
                .map(
                        tune -> new TrackTuneResponse(
                                tune.seq(),
                                tune.tuneTitle(),
                                tune.composerCreditOverride(),
                                tune.arrangerCreditOverride(),
                                tune.linkUrl()))
                .toList();
    }

    private static List<ExternalAudioResponse> toExternalAudioResponses(AlbumView view) {
        return view.externalAudios().stream()
                .map(
                        audio -> new ExternalAudioResponse(
                                audio.externalAudioId(),
                                audio.displayOrder(),
                                audio.url()))
                .toList();
    }

    private static AlbumResponse toAlbumResponse(AlbumView view) {
        return new AlbumResponse(
                view.albumId(),
                view.title(),
                view.releaseDate(),
                view.artistDisplayName(),
                view.artistSortKey(),
                view.description(),
                view.descriptionFormat(),
                view.catalogNumber(),
                view.isdn(),
                view.eventName(),
                view.eventDate(),
                view.eventPlace(),
                view.eventSpaceNumber(),
                view.eventNote(),
                view.publishedAt(),
                view.coverImageUrl(),
                toExternalAudioResponses(view));
    }
}
