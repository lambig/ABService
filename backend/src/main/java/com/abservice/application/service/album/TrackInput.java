package com.abservice.application.service.album;

import com.abservice.domain.service.TrackAssemblyService;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 曲目の1行の入力DTO
 *
 * <p>
 * 作品の登録（{@link RegisterAlbumWithTracksInput}）と更新（{@link UpdateAlbumInput}）が共有します。曲目は
 * どちらの経路でも並びごと届き、受けた配列がそのまま曲目になります（#391）。
 * </p>
 *
 * <p>
 * <b>トラック番号は運びません。</b> 並びは配列の位置がそのまま表します。{@code trackId} を持つ行は既にあるトラックの
 * 組み直しで、持たない行は新しいトラックです。送られなかった既存のトラックは消えます。
 * </p>
 *
 * @param trackId
 *            既にあるトラックのID（nullable。持たない行は新しいトラックとして扱う）
 * @param title
 *            トラックタイトル（nullable。省略したときはチューン名から組まれる）
 * @param artistDisplayName
 *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param tunes
 *            チューン構成（nullable。未指定は構成なしとして扱う）
 */
public record TrackInput(
        @Nullable String trackId,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable List<@Nullable TrackTuneInput> tunes) {

    /**
     * ドメインサービスの入力値へ変換する
     *
     * <p>
     * 行そのものが無い（配列要素が {@code null}）場合はその位置を保ったまま渡す。行の欠落を {@code tracks[i]} の位置で
     * 返すのは、添字を知っている {@link TrackAssemblyService} の側である。
     * </p>
     *
     * @param tracks
     *            曲目の入力DTO一覧（nullable。要素もnullable）
     * @return ドメインサービスの入力値一覧（入力がnullの場合はnull）
     */
    public static @Nullable List<TrackAssemblyService.@Nullable TrackFields> toFields(
            @Nullable List<@Nullable TrackInput> tracks) {
        return Optional.ofNullable(tracks)
                .map(TrackInput::toFieldList)
                .orElse(null);
    }

    private static List<TrackAssemblyService.@Nullable TrackFields> toFieldList(
            List<@Nullable TrackInput> tracks) {
        return tracks.stream().<TrackAssemblyService.@Nullable TrackFields>map(TrackInput::toFieldsOrNull)
                .toList();
    }

    private static TrackAssemblyService.@Nullable TrackFields toFieldsOrNull(@Nullable TrackInput track) {
        return Optional.ofNullable(track)
                .map(TrackInput::toFields)
                .orElse(null);
    }

    private TrackAssemblyService.TrackFields toFields() {
        return new TrackAssemblyService.TrackFields(
                trackId,
                title,
                artistDisplayName,
                artistSortKey,
                TrackTuneInput.toFields(tunes));
    }
}
