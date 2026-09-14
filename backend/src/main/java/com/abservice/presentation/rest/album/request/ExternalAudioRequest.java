package com.abservice.presentation.rest.album.request;

import com.abservice.application.service.album.ExternalAudioInput;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源1件のリクエスト契約（REST の公開入力契約）
 *
 * <p>
 * 外部からの未検証入力。作品の登録と更新のリクエストが共有する。外部音源も曲目と同じく並びごと届き、送られた配列が そのまま作品の音源になる（#391）。
 * </p>
 *
 * <p>
 * <b>表示順は受け取らない。</b> 並びは配列の位置がそのまま表す。{@code externalAudioId} を持つ行は既にある音源、
 * 持たない行は新しい音源で、送られなかった既存の音源は消える。
 * </p>
 *
 * <p>
 * 埋め込めるホストかどうかの判定はバックエンドが持つ。要求元はURLをそのまま送る。
 * </p>
 *
 * @param externalAudioId
 *            既にある外部音源のID（nullable。持たない行は新しい音源として扱う）
 * @param url
 *            埋め込み元URL
 */
public record ExternalAudioRequest(
        @Nullable String externalAudioId,
        @Nullable String url) {

    /**
     * アプリケーション層の入力DTOへ変換する
     *
     * <p>
     * 行そのものが無い（JSONの配列要素が {@code null}）場合はその位置を保ったまま渡す。行の欠落は検証エラーであり、
     * 位置を合成できる場所（{@code ExternalAudioAssemblyService}）まで届けなければ添字を失う。
     * </p>
     *
     * @param audios
     *            外部音源のリクエスト一覧（nullable。要素もnullable）
     * @return 入力DTO一覧（入力がnullの場合はnull）
     */
    public static @Nullable List<@Nullable ExternalAudioInput> toInputs(
            @Nullable List<@Nullable ExternalAudioRequest> audios) {
        return Optional.ofNullable(audios)
                .map(ExternalAudioRequest::toInputList)
                .orElse(null);
    }

    private static List<@Nullable ExternalAudioInput> toInputList(
            List<@Nullable ExternalAudioRequest> audios) {
        return audios.stream()
                .<@Nullable ExternalAudioInput>map(ExternalAudioRequest::toInputOrNull)
                .toList();
    }

    private static @Nullable ExternalAudioInput toInputOrNull(@Nullable ExternalAudioRequest audio) {
        return Optional.ofNullable(audio)
                .map(ExternalAudioRequest::toInput)
                .orElse(null);
    }

    private ExternalAudioInput toInput() {
        return new ExternalAudioInput(externalAudioId, url);
    }
}
