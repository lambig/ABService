package com.abservice.presentation.rest.album.request;

import com.abservice.application.service.album.TrackInput;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 曲目1行のリクエスト契約（REST の公開入力契約）
 *
 * <p>
 * 外部からの未検証入力。作品の登録と更新のリクエストが共有する。曲目はどちらの経路でも並びごと届き、送られた配列が そのまま作品の曲目になる（#391）。
 * </p>
 *
 * <p>
 * <b>トラック番号は受け取らない。</b> 並びは配列の位置がそのまま表す。{@code trackId} を持つ行は既にあるトラック、
 * 持たない行は新しいトラックで、送られなかった既存のトラックは消える。
 * </p>
 *
 * @param trackId
 *            既にあるトラックのID（nullable。持たない行は新しいトラックとして扱う）
 * @param title
 *            トラックタイトル（nullable。省略したときはチューン名から組まれる）
 * @param artistDisplayName
 *            アーティスト表示名（nullable。未指定時は作品のクレジットを継承）
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param tunes
 *            チューン構成（nullable。未指定は構成なしとして扱う）
 */
public record TrackRequest(
        @Nullable String trackId,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable List<@Nullable TrackTuneRequest> tunes) {

    /**
     * アプリケーション層の入力DTOへ変換する
     *
     * <p>
     * 行そのものが無い（JSONの配列要素が {@code null}）場合はその位置を保ったまま渡す。行の欠落は検証エラーであり、
     * 位置を合成できる場所（{@code TrackAssemblyService}）まで届けなければ添字を失う。
     * </p>
     *
     * @param tracks
     *            曲目のリクエスト一覧（nullable。要素もnullable）
     * @return 入力DTO一覧（入力がnullの場合はnull）
     */
    public static @Nullable List<@Nullable TrackInput> toInputs(
            @Nullable List<@Nullable TrackRequest> tracks) {
        return Optional.ofNullable(tracks)
                .map(TrackRequest::toInputList)
                .orElse(null);
    }

    private static List<@Nullable TrackInput> toInputList(List<@Nullable TrackRequest> tracks) {
        return tracks.stream()
                .<@Nullable TrackInput>map(TrackRequest::toInputOrNull)
                .toList();
    }

    private static @Nullable TrackInput toInputOrNull(@Nullable TrackRequest track) {
        return Optional.ofNullable(track)
                .map(TrackRequest::toInput)
                .orElse(null);
    }

    private TrackInput toInput() {
        return new TrackInput(
                trackId,
                title,
                artistDisplayName,
                artistSortKey,
                TrackTuneRequest.toInputs(tunes));
    }
}
