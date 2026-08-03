package com.abservice.domain.model.vo.tune;

import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * チューンの種類を表す列挙型
 *
 * <p>
 * チューンがトラッド、オリジナル、アレンジのいずれかを表します。
 * </p>
 */
public enum TuneKind {
    /**
     * トラッド（伝統曲）
     */
    TRAD,

    /**
     * オリジナル曲
     */
    ORIGINAL,

    /**
     * アレンジ曲（既存曲のアレンジ版）
     */
    ARRANGEMENT;

    /**
     * 外部入力（文字列）からチューン種別を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や未知の値は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #valueOf(String)} を使用してください。
     * </p>
     *
     * @param value
     *            チューン種別を表す文字列（列挙子名。前後空白は許容）
     * @return 成功時は該当する {@code TuneKind}、失敗時はエラー
     */
    public static Result<TuneKind> fromInput(@Nullable String value) {
        return Policy
                .<String>of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "tuneKind",
                                "チューン種別は必須です",
                                "TUNE_KIND_REQUIRED"))
                .verify(
                        value,
                        Function.identity())
                .flatMap(
                        v -> Policy
                                .of(
                                        TuneKind::isKnownName,
                                        () -> new ErrorResult(
                                                "tuneKind",
                                                "不正なチューン種別です: " + v,
                                                "TUNE_KIND_INVALID"))
                                .verify(v, valid -> valueOf(valid.trim())));
    }

    private static boolean isKnownName(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(
                        v -> Arrays.stream(values())
                                .anyMatch(t -> t.name().equals(v.trim())))
                .isPresent();
    }
}
