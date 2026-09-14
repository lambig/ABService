package com.abservice.domain.service;

import com.abservice.domain.model.aggregate.album.ExternalAudio;
import com.abservice.domain.model.vo.common.ExternalAudioUrl;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * 外部入力から外部音源の並びを組み立てるドメインサービス
 *
 * <p>
 * 外部音源も曲目と同じく**並びごと**届きます（#391）。組み立てた並びは {@code Album#replaceExternalAudios} が
 * そのまま受け取り、表示順は配列の位置から振られます。
 * </p>
 *
 * <p>
 * <b>既存の行はIDで見分けます。</b> {@code externalAudioId} を持つ行は既にある行、持たない行は新しい行です。
 * 送られなかった既存の行は、置き換えによって消えます。
 * </p>
 *
 * <p>
 * 埋め込めるホストかどうかの判定は {@link ExternalAudioUrl} が持ちます。同一URLが並びに2度現れることは集約の
 * 不変条件で、{@code Album} 側が拒みます。
 * </p>
 */
@ApplicationScoped
public class ExternalAudioAssemblyService implements DomainService {

    /**
     * 外部入力の一覧から外部音源の並びを組み立てる
     *
     * <p>
     * エラーの位置は本サービスの入力の綴りで、各行は {@code externalAudios[i].} を冠する（DECISIONS 29）。
     * </p>
     *
     * @param audios
     *            外部音源の入力値一覧（nullable。未指定は音源なしとして扱う。要素がnullの行は検証エラーとして扱う）
     * @return 成功時は外部音源の並び、失敗時はエラー
     */
    public Result<List<ExternalAudio>> resolveExternalAudios(
            @Nullable List<@Nullable ExternalAudioFields> audios) {
        return Optional.ofNullable(audios)
                .map(ExternalAudioAssemblyService::validateAudios)
                .orElseGet(() -> Result.success(List.of()));
    }

    /**
     * 外部音源1件の入力値
     *
     * @param externalAudioId
     *            既にある外部音源のID（nullable。持たない行は新しい音源として扱う）
     * @param url
     *            埋め込み元URL
     */
    public record ExternalAudioFields(
            @Nullable String externalAudioId,
            @Nullable String url) {
    }

    private static Result<List<ExternalAudio>> validateAudios(
            List<@Nullable ExternalAudioFields> audios) {
        return Result.all(
                IntStream.range(0, audios.size())
                        .mapToObj(index -> validateAudioAt(audios.get(index), index))
                        .toList());
    }

    private static Result<ExternalAudio> validateAudioAt(
            @Nullable ExternalAudioFields audio,
            int index) {
        return Optional.ofNullable(audio)
                .map(
                        present -> validateAudio(present, index + 1)
                                .mapErrorFields(field -> "externalAudios[" + index + "]." + field))
                .orElseGet(() -> Result.<ExternalAudio>failure(missingAudio(index)));
    }

    /** 行そのものが無い場合は、その要素の位置を指す（項目のパスを持たないため添字までで止める）。 */
    private static ErrorResult missingAudio(int index) {
        return new ErrorResult(
                "externalAudios[" + index + "]",
                "外部音源の情報は必須です",
                "EXTERNAL_AUDIO_REQUIRED");
    }

    private static Result<ExternalAudio> validateAudio(ExternalAudioFields fields, int displayOrder) {
        return ExternalAudioUrl.fromInput(fields.url())
                .withErrorField("url")
                .flatMap(
                        url -> assemble(
                                fields.externalAudioId(),
                                displayOrder,
                                url));
    }

    /** IDを持つ行は組み直し、持たない行は新しい音源。 */
    private static Result<ExternalAudio> assemble(
            @Nullable String externalAudioId,
            int displayOrder,
            ExternalAudioUrl url) {
        return Optional.ofNullable(externalAudioId)
                .filter(StringUtils::isNotBlank)
                .map(
                        id -> reassemble(
                                id,
                                displayOrder,
                                url))
                .orElseGet(() -> Result.success(ExternalAudio.create(displayOrder, url)));
    }

    private static Result<ExternalAudio> reassemble(
            String externalAudioId,
            int displayOrder,
            ExternalAudioUrl url) {
        return ExternalAudio.Id.fromInput(externalAudioId)
                .mapErrorFields(field -> "externalAudioId")
                .map(
                        id -> ExternalAudio.reconstruct(
                                id,
                                displayOrder,
                                url));
    }
}
