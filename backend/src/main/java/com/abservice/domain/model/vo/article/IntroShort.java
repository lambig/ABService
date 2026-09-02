package com.abservice.domain.model.vo.article;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * ショート紹介文の値オブジェクト
 *
 * <p>
 * お品書きの1行や一覧カードの説明文に使う短い紹介文を表します。以下の制約を持ちます：
 * </p>
 * <ul>
 * <li>nullは許可されません（紹介文なしは {@link #EMPTY}）</li>
 * <li>最大長は120文字です（{@code article.intro_short} カラムの上限に一致）</li>
 * </ul>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            ショート紹介文（non-null、紹介文なしは空文字列）
 */
public record IntroShort(@NonNull String value) implements ValueObject<IntroShort> {

    /** ショート紹介文の最大長 */
    private static final int MAX_LENGTH = 120;

    /** 紹介文なしを表す空のショート紹介文。完全に使い回せる定数。 */
    public static final IntroShort EMPTY = new IntroShort("");

    /** 紹介文なし（null・空白のみの入力）を表す検証結果。完全に使い回せる定数。 */
    private static final Result<IntroShort> EMPTY_RESULT = Result.success(EMPTY);

    /** null違反時のエラー */
    private static final ErrorResult REQUIRED_ERROR = new ErrorResult(
            "introShort",
            "ショート紹介文にnullは指定できません",
            "ARTICLE_INTRO_SHORT_REQUIRED");

    /** 最大長超過時のエラー */
    private static final ErrorResult TOO_LONG_ERROR = new ErrorResult(
            "introShort",
            "ショート紹介文は" + MAX_LENGTH + "文字以内です",
            "ARTICLE_INTRO_SHORT_TOO_LONG");

    /**
     * コンストラクタ
     *
     * @param value
     *            ショート紹介文
     * @throws IllegalArgumentException
     *             紹介文がnullの場合、または最大長を超える場合
     */
    public IntroShort {
        introShortPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            ショート紹介文
     * @return IntroShortインスタンス
     */
    public static @NonNull IntroShort of(@NonNull String value) {
        return new IntroShort(value);
    }

    /**
     * 外部入力（文字列）からショート紹介文を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定（{@code null}）と空白のみの入力は
     * {@link #EMPTY} として扱い、最大長超過を {@code Failure} として返します。 信頼できる内部生成には
     * {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            ショート紹介文を表す文字列（{@code null} と空白のみは紹介文なしとして扱う）
     * @return 成功時は {@code IntroShort}、失敗時はエラー
     */
    public static Result<IntroShort> fromInput(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(v -> introShortPolicy().verify(v, IntroShort::new))
                .orElse(EMPTY_RESULT);
    }

    private static Policy<String> introShortPolicy() {
        return Policy.all(
                Policy.of(
                        Objects::nonNull,
                        REQUIRED_ERROR),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        TOO_LONG_ERROR));
    }

    /**
     * 紹介文が空かどうか
     *
     * @return 空の場合true
     */
    public boolean isEmpty() {
        return value.isBlank();
    }

    @Override
    public boolean equivalentTo(IntroShort other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
