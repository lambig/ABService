package com.abservice.application.service.album;

import com.abservice.domain.service.TrackAssemblyService;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * トラック内チューン構成1件の入力DTO
 *
 * <p>
 * トラックの一部として運ばれ、単独のコマンドを持ちません。作品の登録・更新のどちらもこの形で受けます。
 * </p>
 *
 * <p>
 * <b>登場順は運びません</b>（#391）。並びは配列の位置がそのまま表し、番号は組み立ての側が振ります。
 * </p>
 *
 * <p>
 * {@code tuneId} も運びません。{@code Tune} マスタとの同定を行わないため、チューンの手がかりは
 * {@code tuneTitle}（人が書いた記述）だけです。
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
public record TrackTuneInput(
        @Nullable String tuneTitle,
        @Nullable String composerCreditOverride,
        @Nullable String arrangerCreditOverride,
        @Nullable String linkUrl) {

    /**
     * ドメインサービスの入力値へ変換する
     *
     * <p>
     * 行そのものが無い（配列要素が {@code null}）場合はその位置を保ったまま渡す。行の欠落を検証エラーとして {@code tunes[i]}
     * の位置で返すのは、添字を知っている {@link TrackAssemblyService} の側である。
     * </p>
     *
     * @param tunes
     *            チューン構成の入力DTO一覧（nullable。要素もnullable）
     * @return ドメインサービスの入力値一覧（入力がnullの場合はnull）
     */
    public static @Nullable List<TrackAssemblyService.@Nullable TuneFields> toFields(
            @Nullable List<@Nullable TrackTuneInput> tunes) {
        return Optional.ofNullable(tunes)
                .map(TrackTuneInput::toFieldList)
                .orElse(null);
    }

    private static List<TrackAssemblyService.@Nullable TuneFields> toFieldList(
            List<@Nullable TrackTuneInput> tunes) {
        return tunes.stream().<TrackAssemblyService.@Nullable TuneFields>map(TrackTuneInput::toFieldsOrNull)
                .toList();
    }

    private static TrackAssemblyService.@Nullable TuneFields toFieldsOrNull(@Nullable TrackTuneInput tune) {
        return Optional.ofNullable(tune)
                .map(TrackTuneInput::toFields)
                .orElse(null);
    }

    private TrackAssemblyService.TuneFields toFields() {
        return new TrackAssemblyService.TuneFields(
                tuneTitle,
                composerCreditOverride,
                arrangerCreditOverride,
                linkUrl);
    }
}
