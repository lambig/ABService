package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバムのラベルタグを表す列挙型
 *
 * <p>
 * お品書き等で使用するアルバムの分類ラベルです。
 * </p>
 */
public enum LabelTag {
    /**
     * 新譜
     */
    NEW,

    /**
     * ベストアルバム
     */
    BEST_OF,

    /**
     * コンピレーション
     */
    COMPILATION,

    /**
     * コラボレーション
     */
    COLLAB,

    /**
     * その他
     */
    OTHER;

    /**
     * 外部入力（文字列）からラベルタグを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や未知の値は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #valueOf(String)} を使用してください。
     * </p>
     *
     * @param value
     *            ラベルタグを表す文字列（列挙子名。前後空白は許容）
     * @return 成功時は該当する {@code LabelTag}、失敗時はエラー
     */
    public static Result<LabelTag> fromInput(@Nullable String value) {
        return Policy
                .<String>of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "labelTag",
                                "ラベルタグは必須です",
                                "LABEL_TAG_REQUIRED"))
                .verify(
                        value,
                        Function.identity())
                .flatMap(
                        v -> Policy
                                .of(
                                        LabelTag::isKnownName,
                                        () -> new ErrorResult(
                                                "labelTag",
                                                "不正なラベルタグです: " + v,
                                                "LABEL_TAG_INVALID"))
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
