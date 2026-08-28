package com.abservice.presentation.rest.album;

import com.abservice.application.query.album.GetAlbumResult;
import com.abservice.application.query.album.ListAlbumsResult;
import com.abservice.application.query.album.model.AlbumView;
import com.abservice.presentation.rest.album.response.AdminAlbumDetailResponse;
import com.abservice.presentation.rest.album.response.AdminAlbumListResponse;
import com.abservice.presentation.rest.album.response.AdminAlbumResponse;
import com.abservice.presentation.rest.album.response.AdminExternalAudioResponse;
import com.abservice.presentation.rest.album.response.AdminTrackResponse;
import com.abservice.presentation.rest.album.response.PublicAlbumDetailResponse;
import com.abservice.presentation.rest.album.response.PublicAlbumListResponse;
import com.abservice.presentation.rest.album.response.PublicAlbumResponse;
import com.abservice.presentation.rest.album.response.PublicExternalAudioResponse;
import com.abservice.presentation.rest.album.response.PublicTrackResponse;
import com.abservice.presentation.rest.album.response.TrackTuneResponse;
import com.abservice.presentation.rest.exception.ProblemDetail;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * アルバム照会結果から HTTP 応答への変換
 *
 * <p>
 * 公開向け（{@link AlbumQueryResource}）と管理向け（{@link AlbumAdminQueryResource}）は対象範囲だけでなく
 * 応答表現も異なるため、要求元ごとに変換を持つ。未存在（404）の表現は共通のため、本クラスに集約する。
 * </p>
 *
 * <p>
 * 詳細と一覧でも応答表現が異なる。詳細は概要説明・外部音源・曲目を返し、一覧は作品を選ぶための表示に留める （`docs/DECISIONS.md`
 * 20）。
 * </p>
 */
final class AlbumQueryResponses {

    private static final String PROBLEM_JSON = "application/problem+json";

    private AlbumQueryResponses() {
    }

    /**
     * 詳細照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会したアルバムのドメインID
     * @return 200 とアルバム詳細、未存在時は 404 の Problem Details
     */
    static Response toPublicResponse(GetAlbumResult result, String id) {
        return toResponse(
                result,
                id,
                AlbumQueryResponses::toPublicAlbumDetailResponse);
    }

    /**
     * 詳細照会結果を管理向けの応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会したアルバムのドメインID
     * @return 200 とアルバム詳細、未存在時は 404 の Problem Details
     */
    static Response toAdminResponse(GetAlbumResult result, String id) {
        return toResponse(
                result,
                id,
                AlbumQueryResponses::toAdminAlbumDetailResponse);
    }

    /**
     * 一覧照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 200 とアルバム一覧
     */
    static Response toPublicListResponse(ListAlbumsResult result) {
        return Response.ok(
                new PublicAlbumListResponse(
                        result.items().stream().map(AlbumQueryResponses::toPublicAlbumResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }

    /**
     * 一覧照会結果を管理向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return 200 とアルバム一覧
     */
    static Response toAdminListResponse(ListAlbumsResult result) {
        return Response.ok(
                new AdminAlbumListResponse(
                        result.items().stream().map(AlbumQueryResponses::toAdminAlbumResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()))
                .build();
    }

    private static <T> Response toResponse(
            GetAlbumResult result,
            String id,
            Function<AlbumView, T> toDetailResponse) {
        return switch (result) {
            case GetAlbumResult.Found(var album) -> Response.ok(toDetailResponse.apply(album)).build();
            case GetAlbumResult.NotFound() -> Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.valueOf(PROBLEM_JSON)).entity(notFoundProblem(id)).build();
        };
    }

    private static ProblemDetail notFoundProblem(String id) {
        return ProblemDetail.of(
                "ENTITY_NOT_FOUND",
                "Resource not found",
                Response.Status.NOT_FOUND.getStatusCode(),
                "Album not found: id=" + id,
                List.of());
    }

    private static PublicAlbumDetailResponse toPublicAlbumDetailResponse(AlbumView view) {
        return new PublicAlbumDetailResponse(
                view.albumId(),
                view.title(),
                view.releaseDate(),
                view.artistDisplayName(),
                view.description(),
                view.descriptionFormat(),
                view.catalogNumber(),
                view.isdn(),
                view.eventName(),
                view.eventDate(),
                view.eventPlace(),
                view.eventSpaceNumber(),
                view.eventNote(),
                publicPublishedAt(view),
                view.coverImageUrl(),
                toPublicExternalAudioResponses(view),
                toPublicTrackResponses(view));
    }

    private static PublicAlbumResponse toPublicAlbumResponse(AlbumView view) {
        return new PublicAlbumResponse(
                view.albumId(),
                view.title(),
                view.releaseDate(),
                view.artistDisplayName(),
                view.catalogNumber(),
                view.isdn(),
                view.eventName(),
                view.eventDate(),
                view.eventPlace(),
                view.eventSpaceNumber(),
                view.eventNote(),
                publicPublishedAt(view),
                view.coverImageUrl());
    }

    private static AdminAlbumDetailResponse toAdminAlbumDetailResponse(AlbumView view) {
        return new AdminAlbumDetailResponse(
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
                toAdminExternalAudioResponses(view),
                toAdminTrackResponses(view));
    }

    private static AdminAlbumResponse toAdminAlbumResponse(AlbumView view) {
        return new AdminAlbumResponse(
                view.albumId(),
                view.title(),
                view.releaseDate(),
                view.artistDisplayName(),
                view.catalogNumber(),
                view.isdn(),
                view.eventName(),
                view.eventDate(),
                view.eventPlace(),
                view.eventSpaceNumber(),
                view.eventNote(),
                view.publishedAt(),
                view.coverImageUrl());
    }

    /*
     * CONTRACT: 公開向けの応答は公開日時を必ず持つ。共有の Read Model は下書きを含むため nullable だが、公開向けの
     * 照会は公開中のものだけを返すため、この境界で非nullへ絞る。破れていた場合に null を公開契約として返すのではなく、 ここで検出する。
     */
    private static Instant publicPublishedAt(AlbumView view) {
        return Objects.requireNonNull(
                view.publishedAt(),
                "公開向けの照会結果は公開中のものに限るため、publishedAt は値を持つ");
    }

    private static List<PublicTrackResponse> toPublicTrackResponses(AlbumView view) {
        return view.tracks().stream()
                .map(
                        track -> new PublicTrackResponse(
                                track.trackNo(),
                                track.title(),
                                track.artistDisplayName(),
                                toTrackTuneResponses(track)))
                .toList();
    }

    private static List<AdminTrackResponse> toAdminTrackResponses(AlbumView view) {
        return view.tracks().stream()
                .map(
                        track -> new AdminTrackResponse(
                                track.trackId(),
                                track.trackNo(),
                                track.title(),
                                track.artistDisplayName(),
                                track.artistSortKey(),
                                toTrackTuneResponses(track)))
                .toList();
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

    private static List<PublicExternalAudioResponse> toPublicExternalAudioResponses(AlbumView view) {
        return view.externalAudios().stream()
                .map(
                        audio -> new PublicExternalAudioResponse(
                                audio.displayOrder(),
                                audio.url()))
                .toList();
    }

    private static List<AdminExternalAudioResponse> toAdminExternalAudioResponses(AlbumView view) {
        return view.externalAudios().stream()
                .map(
                        audio -> new AdminExternalAudioResponse(
                                audio.externalAudioId(),
                                audio.displayOrder(),
                                audio.url()))
                .toList();
    }
}
