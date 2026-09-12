package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * 原作の出典の記述の値オブジェクト
 *
 * <p>
 * アレンジ集の「「○○」より各曲」のように、<b>何をアレンジしたかを人が書いた一文</b>を表します。
 * 作品名を構造として持つものではありません——「より各曲」のような言い回しは作者の主張であり、
 * 作品名だけを持って表示側で組み立てると、別の盤（「メインテーマのみ」など）では嘘になります。
 * </p>
 *
 * <p>
 * したがって<b>同定のキーにしません</b>。1つの作品を指すとは限らず、綴りで寄せると別々の作品が 1つになります（#183
 * のチューンの同定と同じ理由）。
 * </p>
 *
 * @param value
 *            原作の出典の記述
 */
public record OriginalWorkNote(String value) implements ValueObject<OriginalWorkNote> {

    /** 列（`album.original_work_note`）が持つ長さと揃える */
    private static final int MAX_LENGTH = 255;

    /**
     * コンストラクタ
     *
     * @param value
     *            原作の出典の記述
     * @throws IllegalArgumentException
     *             空白のみ、または最大長を超える場合
     */
    public OriginalWorkNote {
        notePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            原作の出典の記述
     * @return OriginalWorkNote
     */
    public static OriginalWorkNote of(String value) {
        return new OriginalWorkNote(value);
    }

    /**
     * 外部入力（文字列）から生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。信頼できる内部生成には {@link #of(String)}
     * を使用してください。
     * </p>
     *
     * @param value
     *            原作の出典の記述を表す文字列
     * @return 成功時は {@code OriginalWorkNote}、失敗時はエラー
     */
    public static Result<OriginalWorkNote> fromInput(@Nullable String value) {
        return notePolicy().verify(value, OriginalWorkNote::new);
    }

    private static Policy<String> notePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "originalWorkNote",
                                "Original work note cannot be blank",
                                "ORIGINAL_WORK_NOTE_BLANK")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult(
                                "originalWorkNote",
                                "Original work note must be " + MAX_LENGTH + " characters or less",
                                "ORIGINAL_WORK_NOTE_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(OriginalWorkNote other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
