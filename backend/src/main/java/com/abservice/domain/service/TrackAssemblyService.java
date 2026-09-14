package com.abservice.domain.service;

import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * 外部入力からトラックの並びを組み立てるドメインサービス
 *
 * <p>
 * 作品の曲目は、作るときも直すときも**並びごと**届きます（#391）。1件ずつ足す口はもう無いため、本サービスが受けるのも 一覧で、組み立てた並びは
 * {@code Album#replaceTracks} がそのまま受け取ります。作品の登録
 * （{@code com.abservice.application.service.album.RegisterAlbumWithTracksService}）と更新
 * （{@code com.abservice.application.service.album.UpdateAlbumService}）が共有するため、特定の
 * CommandService に属さないドメインサービスとして切り出しています。
 * </p>
 *
 * <p>
 * <b>番号は入力が持ちません。</b> トラック番号もチューンの登場順も、受け取った配列の位置から振ります。人に番号を
 * 入れさせると、行を入れ替えるたびに振り直す作業が要るうえ、番号の重複・欠番という失敗が入力の側に生まれます。
 * </p>
 *
 * <p>
 * <b>既存の行はIDで見分けます。</b> {@code trackId} を持つ行は既にある行の組み直しで、持たない行は新しい行です。
 * 送られなかった既存の行は、置き換えによって消えます。
 * </p>
 */
@ApplicationScoped
public class TrackAssemblyService implements DomainService {

    /**
     * 外部入力の一覧からトラックの並びを組み立てる
     *
     * <p>
     * 検証エラーは {@link Result} で返す。例外へ変えるのは呼び出し元（application 層）の判断である（DECISIONS 29）。
     * エラーの位置は本サービスの入力の綴りで、各行は {@code tracks[i].} を、行の中のチューン構成はさらに {@code tunes[i].}
     * を冠する。添字を知っているのは一覧を受け取るここだけで、落とすとどの行が不正なのかを 呼び出し元が特定できない。
     * </p>
     *
     * @param tracks
     *            トラックの入力値一覧（nullable。未指定は曲目なしとして扱う。要素がnullの行は検証エラーとして扱う）
     * @return 成功時はトラックの並び、失敗時はエラー
     */
    public Result<List<Track.Row>> resolveTracks(@Nullable List<@Nullable TrackFields> tracks) {
        return Optional.ofNullable(tracks)
                .map(TrackAssemblyService::validateTracks)
                .orElseGet(() -> Result.success(List.of()));
    }

    /**
     * トラック1件の入力値
     *
     * @param trackId
     *            既にあるトラックのID（nullable。持たない行は新しいトラックとして扱う）
     * @param title
     *            トラックタイトル（nullable。省略したときはチューン名を繋いだものが名になる。#360）
     * @param artistDisplayName
     *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
     * @param artistSortKey
     *            アーティストソートキー（nullable）
     * @param tunes
     *            チューン構成（nullable。未指定は構成なしとして扱う。要素がnullの行は検証エラーとして扱う）
     */
    public record TrackFields(
            @Nullable String trackId,
            @Nullable String title,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            @Nullable List<@Nullable TuneFields> tunes) {
    }

    /**
     * トラック内のチューン構成1件の入力値
     *
     * @param tuneTitle
     *            チューン名（nullable）
     * @param composerCreditOverride
     *            作曲者クレジット（nullable）
     * @param arrangerCreditOverride
     *            アレンジャークレジット（nullable）
     * @param linkUrl
     *            リンクURL（nullable）
     */
    public record TuneFields(
            @Nullable String tuneTitle,
            @Nullable String composerCreditOverride,
            @Nullable String arrangerCreditOverride,
            @Nullable String linkUrl) {
    }

    private static Result<List<Track.Row>> validateTracks(List<@Nullable TrackFields> tracks) {
        return Result.all(
                IntStream.range(0, tracks.size())
                        .mapToObj(index -> validateTrackAt(tracks.get(index), index))
                        .toList());
    }

    private static Result<Track.Row> validateTrackAt(@Nullable TrackFields track, int index) {
        return Optional.ofNullable(track)
                .map(
                        present -> validateTrack(present)
                                .mapErrorFields(field -> "tracks[" + index + "]." + field))
                .orElseGet(() -> Result.<Track.Row>failure(missingTrack(index)));
    }

    /** 行そのものが無い場合は、その要素の位置を指す（項目のパスを持たないため添字までで止める）。 */
    private static ErrorResult missingTrack(int index) {
        return new ErrorResult(
                "tracks[" + index + "]",
                "トラック情報は必須です",
                "TRACK_REQUIRED");
    }

    private static Result<Track.Row> validateTrack(TrackFields fields) {
        return Result.zip(
                resolveTrackId(fields.trackId()),
                resolveArtistCredit(fields.artistDisplayName(), fields.artistSortKey())
                        .withErrorField("artistDisplayName"),
                resolveTunes(fields.tunes()),
                ResolvedFields::new)
                .flatMap(
                        resolved -> Track.rowFromInput(
                                resolved.trackId().orElse(null),
                                fields.title(),
                                resolved.artistCredit().orElse(null),
                                resolved.tunes()));
    }

    /**
     * IDの綴りだけを解く。
     *
     * <p>
     * <b>そのIDが対象の作品の子であるかは、ここでは決まらない。</b> 子の識別は親の中でしか意味を持たないため、
     * 確かめられるのは集約（{@code Album#replaceTracks}）だけである。ここが答えるのは「識別子として読めるか」までで、
     * 持たない行は新しいトラックになる。
     * </p>
     */
    private static Result<Optional<Track.Id>> resolveTrackId(@Nullable String trackId) {
        return Optional.ofNullable(trackId)
                .filter(StringUtils::isNotBlank)
                .map(
                        given -> Track.Id.fromInput(given)
                                .mapErrorFields(field -> "trackId")
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<Track.Id>>success(Optional.empty()));
    }

    private record ResolvedFields(
            Optional<Track.Id> trackId,
            Optional<ArtistCredit> artistCredit,
            List<TrackTune> tunes) {
    }

    private static Result<List<TrackTune>> resolveTunes(@Nullable List<@Nullable TuneFields> tunes) {
        return Optional.ofNullable(tunes)
                .map(TrackAssemblyService::validateTunes)
                .orElseGet(() -> Result.success(List.of()));
    }

    private static Result<List<TrackTune>> validateTunes(List<@Nullable TuneFields> tunes) {
        return Result.all(
                IntStream.range(0, tunes.size())
                        .mapToObj(index -> validateTuneAt(tunes.get(index), index))
                        .toList());
    }

    private static Result<TrackTune> validateTuneAt(@Nullable TuneFields tune, int index) {
        return Optional.ofNullable(tune)
                .map(
                        present -> validateTune(present, index + 1)
                                .mapErrorFields(field -> "tunes[" + index + "]." + field))
                .orElseGet(() -> Result.<TrackTune>failure(missingTune(index)));
    }

    /** 行そのものが無い場合は、その要素の位置を指す（項目のパスを持たないため添字までで止める）。 */
    private static ErrorResult missingTune(int index) {
        return new ErrorResult(
                "tunes[" + index + "]",
                "Tune information is required",
                "TUNE_REQUIRED");
    }

    private static Result<TrackTune> validateTune(TuneFields tune, int seq) {
        return TrackTune.fromInput(
                seq,
                tune.tuneTitle(),
                tune.composerCreditOverride(),
                tune.arrangerCreditOverride(),
                tune.linkUrl());
    }

    private static Result<Optional<ArtistCredit>> resolveArtistCredit(
            @Nullable String displayName,
            @Nullable String sortKey) {
        return Optional.ofNullable(displayName)
                .filter(StringUtils::isNotBlank)
                .map(
                        name -> ArtistCredit.fromInput(name, sortKey)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<ArtistCredit>>success(Optional.empty()));
    }
}
