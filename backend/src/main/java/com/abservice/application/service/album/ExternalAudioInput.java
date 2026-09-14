package com.abservice.application.service.album;

import com.abservice.domain.service.ExternalAudioAssemblyService;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源1件の入力DTO
 *
 * <p>
 * 作品の登録（{@link RegisterAlbumWithTracksInput}）と更新（{@link UpdateAlbumInput}）が共有します。外部音源も
 * 曲目と同じく並びごと届き、受けた配列がそのまま並びになります（#391）。
 * </p>
 *
 * <p>
 * <b>表示順は運びません。</b> 並びは配列の位置がそのまま表します。{@code externalAudioId} を持つ行は既にある音源で、
 * 持たない行は新しい音源です。送られなかった既存の音源は消えます。
 * </p>
 *
 * @param externalAudioId
 *            既にある外部音源のID（nullable。持たない行は新しい音源として扱う）
 * @param url
 *            埋め込み元URL
 */
public record ExternalAudioInput(
        @Nullable String externalAudioId,
        @Nullable String url) {

    /**
     * ドメインサービスの入力値へ変換する
     *
     * <p>
     * 行そのものが無い（配列要素が {@code null}）場合はその位置を保ったまま渡す。行の欠落を {@code externalAudios[i]}
     * の位置で返すのは、添字を知っている {@link ExternalAudioAssemblyService} の側である。
     * </p>
     *
     * @param audios
     *            外部音源の入力DTO一覧（nullable。要素もnullable）
     * @return ドメインサービスの入力値一覧（入力がnullの場合はnull）
     */
    public static @Nullable List<ExternalAudioAssemblyService.@Nullable ExternalAudioFields> toFields(
            @Nullable List<@Nullable ExternalAudioInput> audios) {
        return Optional.ofNullable(audios)
                .map(ExternalAudioInput::toFieldList)
                .orElse(null);
    }

    private static List<ExternalAudioAssemblyService.@Nullable ExternalAudioFields> toFieldList(
            List<@Nullable ExternalAudioInput> audios) {
        return audios.stream().<ExternalAudioAssemblyService
                .@Nullable ExternalAudioFields>map(ExternalAudioInput::toFieldsOrNull)
                .toList();
    }

    private static ExternalAudioAssemblyService.@Nullable ExternalAudioFields toFieldsOrNull(
            @Nullable ExternalAudioInput audio) {
        return Optional.ofNullable(audio)
                .map(ExternalAudioInput::toFields)
                .orElse(null);
    }

    private ExternalAudioAssemblyService.ExternalAudioFields toFields() {
        return new ExternalAudioAssemblyService.ExternalAudioFields(externalAudioId, url);
    }
}
