package com.abservice.presentation.rest.album;

import com.abservice.application.query.album.GetAlbumPreconditionsResult;
import com.abservice.application.query.album.GetAlbumResult;
import com.abservice.application.query.album.ListAlbumsResult;
import com.abservice.application.query.album.model.AlbumView;
import com.abservice.application.query.album.model.DeletionEffectView;
import com.abservice.application.query.album.model.UnpublicationEffectView;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.presentation.rest.album.response.AdminAlbumDetailResponse;
import com.abservice.presentation.rest.album.response.AdminAlbumListResponse;
import com.abservice.presentation.rest.album.response.AdminAlbumResponse;
import com.abservice.presentation.rest.album.response.AdminExternalAudioResponse;
import com.abservice.presentation.rest.album.response.AdminTrackResponse;
import com.abservice.presentation.rest.album.response.AlbumDeletionPreconditionsResponse;
import com.abservice.presentation.rest.album.response.AlbumPreconditionsResponse;
import com.abservice.presentation.rest.album.response.AlbumUnpublicationPreconditionsResponse;
import com.abservice.presentation.rest.album.response.PublicAlbumDetailResponse;
import com.abservice.presentation.rest.album.response.PublicAlbumListResponse;
import com.abservice.presentation.rest.album.response.PublicAlbumResponse;
import com.abservice.presentation.rest.album.response.PublicExternalAudioResponse;
import com.abservice.presentation.rest.album.response.PublicTrackResponse;
import com.abservice.presentation.rest.album.response.TrackTuneResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * アルバム照会結果から HTTP 応答への変換
 *
 * <p>
 * 公開向け（{@link AlbumQueryResource}）と管理向け（{@link AlbumAdminQueryResource}）は対象範囲だけでなく
 * 応答表現も異なるため、要求元ごとに変換を持つ。未存在の扱いは共通のため、本クラスに集約する。
 * </p>
 *
 * <p>
 * 未存在は {@link EntityNotFoundException} を投げ、HTTP への変換は
 * {@code presentation.rest.exception.DomainExceptionMapper}
 * に委ねる。応答本体の型を返すことで、API 定義の レスポンススキーマが実装から生成される。
 * </p>
 *
 * <p>
 * 詳細と一覧でも応答表現が異なる。詳細は概要説明・外部音源・曲目を返し、一覧は作品を選ぶための表示に留める （`docs/DECISIONS.md`
 * 20）。
 * </p>
 */
final class AlbumQueryResponses {

    private static final String ENTITY_NAME = "Album";

    private AlbumQueryResponses() {
    }

    /**
     * 操作の前提の照会結果を応答へ変換します。
     *
     * @param result
     *            前提の照会結果
     * @param id
     *            照会したアルバムのドメインID
     * @param operation
     *            問われた操作の綴り
     * @return 操作の前提
     * @throws EntityNotFoundException
     *             アルバムが存在しない場合
     */
    static AlbumPreconditionsResponse toPreconditionsResponse(
            GetAlbumPreconditionsResult result,
            String id,
            String operation) {
        return switch (result) {
            case GetAlbumPreconditionsResult.Deletion(var affected) -> AlbumPreconditionsResponse.ofDeletion(
                    operation,
                    new AlbumDeletionPreconditionsResponse(
                            affected.stream()
                                    .map(AlbumQueryResponses::toAffectedArticle)
                                    .toList()));
            case GetAlbumPreconditionsResult.Unpublication(var becomingUnpublished) -> AlbumPreconditionsResponse
                    .ofUnpublication(
                            operation,
                            new AlbumUnpublicationPreconditionsResponse(
                                    becomingUnpublished.stream()
                                            .map(AlbumQueryResponses::toCascadeUnpublishedArticle)
                                            .toList()));
            case GetAlbumPreconditionsResult.NotFound() -> throw EntityNotFoundException.of(ENTITY_NAME, id);
        };
    }

    private static AlbumDeletionPreconditionsResponse.AffectedArticle toAffectedArticle(DeletionEffectView view) {
        return new AlbumDeletionPreconditionsResponse.AffectedArticle(
                view.articleId(),
                view.title(),
                view.losesAlbumReference(),
                view.becomesUnpublished());
    }

    private static AlbumUnpublicationPreconditionsResponse.CascadeUnpublishedArticle toCascadeUnpublishedArticle(
            UnpublicationEffectView view) {
        return new AlbumUnpublicationPreconditionsResponse.CascadeUnpublishedArticle(
                view.articleId(),
                view.title());
    }

    /**
     * 詳細照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            詳細照会結果
     * @param id
     *            照会したアルバムのドメインID
     * @return アルバム詳細
     * @throws EntityNotFoundException
     *             公開中のアルバムが存在しない場合
     */
    static PublicAlbumDetailResponse toPublicResponse(GetAlbumResult result, String id) {
        return toDetail(
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
     * @return アルバム詳細
     * @throws EntityNotFoundException
     *             アルバムが存在しない場合
     */
    static AdminAlbumDetailResponse toAdminResponse(GetAlbumResult result, String id) {
        return toDetail(
                result,
                id,
                AlbumQueryResponses::toAdminAlbumDetailResponse);
    }

    /**
     * 一覧照会結果を公開向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return アルバム一覧
     */
    static PublicAlbumListResponse toPublicListResponse(ListAlbumsResult result) {
        return new PublicAlbumListResponse(
                result.items().stream().map(AlbumQueryResponses::toPublicAlbumResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    /**
     * 一覧照会結果を管理向けの応答へ変換します。
     *
     * @param result
     *            一覧照会結果
     * @return アルバム一覧
     */
    static AdminAlbumListResponse toAdminListResponse(ListAlbumsResult result) {
        return new AdminAlbumListResponse(
                result.items().stream().map(AlbumQueryResponses::toAdminAlbumResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private static <T> T toDetail(
            GetAlbumResult result,
            String id,
            Function<AlbumView, T> toDetailResponse) {
        return switch (result) {
            case GetAlbumResult.Found(var album) -> toDetailResponse.apply(album);
            case GetAlbumResult.NotFound() -> throw EntityNotFoundException.of(ENTITY_NAME, id);
        };
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
