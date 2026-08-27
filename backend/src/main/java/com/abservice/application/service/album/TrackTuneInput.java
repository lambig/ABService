package com.abservice.application.service.album;

import com.abservice.domain.service.TrackAdditionService;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * トラック内チューン構成1件の入力DTO
 *
 * <p>
 * トラックを追加・更新・同時登録する各コマンドが共有します。チューン構成はトラックの一部として運ばれ、 単独のコマンドを持ちません。
 * </p>
 *
 * <p>
 * {@code tuneId} は運びません。{@code Tune} マスタとの同定を行わないため、チューンの手がかりは
 * {@code tuneTitle}（人が書いた記述）だけです。
 * </p>
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
public record TrackTuneInput(
        @Nullable Integer seq,
        @Nullable String tuneTitle,
        @Nullable String composerCreditOverride,
        @Nullable String arrangerCreditOverride,
        @Nullable String linkUrl) {

    /**
     * ドメインサービスの入力値へ変換する
     *
     * @param tunes
     *            チューン構成の入力DTO一覧（nullable）
     * @return ドメインサービスの入力値一覧（入力がnullの場合はnull）
     */
    public static @Nullable List<TrackAdditionService.TuneFields> toFields(
            @Nullable List<TrackTuneInput> tunes) {
        return Optional.ofNullable(tunes)
                .map(TrackTuneInput::toFieldList)
                .orElse(null);
    }

    private static List<TrackAdditionService.TuneFields> toFieldList(List<TrackTuneInput> tunes) {
        return tunes.stream()
                .map(TrackTuneInput::toFields)
                .toList();
    }

    private TrackAdditionService.TuneFields toFields() {
        return new TrackAdditionService.TuneFields(
                seq,
                tuneTitle,
                composerCreditOverride,
                arrangerCreditOverride,
                linkUrl);
    }
}
