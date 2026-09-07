package com.abservice.domain.service;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバムへのトラック追加（検証・組み立て）を担うドメインサービス
 *
 * <p>
 * 外部入力の生の値からTrackを検証・組み立て、対象のAlbumに追加するロジックを提供します。単体の
 * {@link com.abservice.application.service.album.AddTrackService}
 * だけでなく、アルバムと同時にトラックを登録するユースケースからも呼ばれる（{@code
 * com.abservice.application.service.album.RegisterAlbumWithTracksService}）ため、
 * 特定のCommandServiceに属さないドメインサービスとして切り出しています。トラック番号の重複はAlbum集約自身が検証します
 * （{@link com.abservice.domain.exception.BusinessRuleViolationException}）。
 * </p>
 *
 * <p>
 * チューン構成の検証（{@link #resolveTunes}）は、トラックを更新するユースケース
 * （{@code com.abservice.application.service.album.UpdateTrackService}）とも共有します。
 * </p>
 */
@ApplicationScoped
public class TrackAdditionService implements DomainService {

    /**
     * 外部入力からトラックを検証・生成し、アルバムに追加する
     *
     * <p>
     * 検証エラーは {@link Result} で返す。例外へ変えるのは呼び出し元（application 層）の判断で、そこは入力を
     * 組み立てた場所として、エラーの位置を自分の入力パスへ写せる立場でもある（DECISIONS 29）。エラーの位置は
     * 本サービスの入力（{@link TrackFields}）の綴りで、チューン構成は {@code tunes[i].} を冠する。
     * </p>
     *
     * @param album
     *            追加先のアルバム
     * @param fields
     *            追加するトラックの入力値
     * @return 成功時は追加後のアルバムと追加されたトラックの組、失敗時はエラー
     */
    public Result<Addition> addTrack(Album album, TrackFields fields) {
        return validate(fields)
                .map(track -> new Addition(album.addTrack(track), track));
    }

    /**
     * 追加するトラックの入力値
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistDisplayName
     *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
     * @param artistSortKey
     *            アーティストソートキー（nullable）
     * @param tunes
     *            チューン構成（nullable。未指定は構成なしとして扱う）
     */
    public record TrackFields(
            @Nullable Integer trackNo,
            @Nullable String title,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            @Nullable List<TuneFields> tunes) {
    }

    /**
     * トラック内のチューン構成1件の入力値
     *
     * @param seq
     *            トラック内での登場順（1, 2, 3, ...）
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
            @Nullable Integer seq,
            @Nullable String tuneTitle,
            @Nullable String composerCreditOverride,
            @Nullable String arrangerCreditOverride,
            @Nullable String linkUrl) {
    }

    /**
     * トラック追加の結果
     *
     * @param album
     *            追加後のアルバム
     * @param track
     *            追加されたトラック
     */
    public record Addition(Album album, Track track) {
    }

    /**
     * 外部入力からチューン構成の一覧を検証・生成する
     *
     * <p>
     * 各行の検証エラーは集約されます。{@code seq} はトラック内で一意である必要があります。
     * </p>
     *
     * @param tunes
     *            チューン構成の入力値（nullable。未指定は構成なしとして扱う）
     * @return 成功時はチューン構成の一覧、失敗時はエラー
     */
    public static Result<List<TrackTune>> resolveTunes(@Nullable List<TuneFields> tunes) {
        return Optional.ofNullable(tunes)
                .map(TrackAdditionService::validateTunes)
                .orElseGet(() -> Result.success(List.of()));
    }

    static Result<Track> validate(TrackFields fields) {
        return Result.zip(
                resolveArtistCredit(fields.artistDisplayName(), fields.artistSortKey())
                        .withErrorField("artistDisplayName"),
                resolveTunes(fields.tunes()),
                ResolvedFields::new)
                .flatMap(
                        resolved -> Track.fromInput(
                                fields.trackNo(),
                                fields.title(),
                                resolved.artistCredit().orElse(null))
                                .map(track -> track.replaceTunes(resolved.tunes())));
    }

    private record ResolvedFields(Optional<ArtistCredit> artistCredit, List<TrackTune> tunes) {
    }

    private static Result<List<TrackTune>> validateTunes(List<TuneFields> tunes) {
        return Result.all(validateEach(tunes))
                .flatMap(TrackAdditionService::verifyUniqueSeqs);
    }

    /**
     * 各行のエラーを、その行の入力パス（{@code tunes[i].<項目>}）へ写す。
     *
     * <p>
     * 添字を落とすと、どの行が不正なのかを呼び出し元が特定できない（{@code TrackTune} は自分が何番目の行かを
     * 知らないため、写せるのは一覧を組み立てるここだけ）。
     * </p>
     */
    private static List<Result<TrackTune>> validateEach(List<TuneFields> tunes) {
        return IntStream.range(0, tunes.size())
                .mapToObj(
                        index -> validateTune(tunes.get(index))
                                .mapErrorFields(field -> "tunes[" + index + "]." + field))
                .toList();
    }

    private static Result<TrackTune> validateTune(TuneFields tune) {
        return TrackTune.fromInput(
                tune.seq(),
                tune.tuneTitle(),
                tune.composerCreditOverride(),
                tune.arrangerCreditOverride(),
                tune.linkUrl());
    }

    private static Result<List<TrackTune>> verifyUniqueSeqs(List<TrackTune> tunes) {
        return Policy.<List<TrackTune>>of(
                resolved -> resolved.stream().map(TrackTune::seq).distinct().count() == resolved.size(),
                () -> new ErrorResult(
                        "tunes",
                        "Tune seq must be unique in this track",
                        "TUNE_SEQ_DUPLICATE"))
                .verify(tunes, Function.identity());
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
