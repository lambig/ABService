package com.abservice.presentation.rest.album.request;

import com.abservice.application.service.album.TrackTuneInput;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * トラック内チューン構成1件のリクエスト契約（REST の公開入力契約）
 *
 * <p>
 * 外部からの未検証入力。値検証はアプリケーション層（各値オブジェクトの {@code fromInput}）に委譲する。
 * 作品の登録と更新のリクエストが共有する。
 * </p>
 *
 * <p>
 * <b>登場順は受け取らない</b>（#391）。並びは配列の位置がそのまま表す。番号を受け取ると、行を入れ替えるたびに
 * 振り直す作業が要求側に生まれ、番号の重複・欠番という失敗も入力の側に生まれる。
 * </p>
 *
 * <p>
 * {@code tuneId} も受け取らない。{@code Tune} マスタとの同定を行わないため、チューンの手がかりは
 * {@code tuneTitle} だけである。
 * </p>
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
public record TrackTuneRequest(
        @Nullable String tuneTitle,
        @Nullable String composerCreditOverride,
        @Nullable String arrangerCreditOverride,
        @Nullable String linkUrl) {

    /**
     * アプリケーション層の入力DTOへ変換する
     *
     * <p>
     * 行そのものが無い（JSONの配列要素が {@code null}）場合はその位置を保ったまま渡す。行の欠落は検証エラーであり、
     * 位置を合成できる場所（{@code TrackAssemblyService}）まで届けなければ添字を失う。
     * </p>
     *
     * @param tunes
     *            チューン構成のリクエスト一覧（nullable。要素もnullable）
     * @return 入力DTO一覧（入力がnullの場合はnull）
     */
    public static @Nullable List<@Nullable TrackTuneInput> toInputs(
            @Nullable List<@Nullable TrackTuneRequest> tunes) {
        return Optional.ofNullable(tunes)
                .map(TrackTuneRequest::toInputList)
                .orElse(null);
    }

    private static List<@Nullable TrackTuneInput> toInputList(List<@Nullable TrackTuneRequest> tunes) {
        return tunes.stream()
                .<@Nullable TrackTuneInput>map(TrackTuneRequest::toInputOrNull)
                .toList();
    }

    private static @Nullable TrackTuneInput toInputOrNull(@Nullable TrackTuneRequest tune) {
        return Optional.ofNullable(tune)
                .map(TrackTuneRequest::toInput)
                .orElse(null);
    }

    private TrackTuneInput toInput() {
        return new TrackTuneInput(
                tuneTitle,
                composerCreditOverride,
                arrangerCreditOverride,
                linkUrl);
    }
}
