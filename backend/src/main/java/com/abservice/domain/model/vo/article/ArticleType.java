package com.abservice.domain.model.vo.article;

import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Arrays;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * 記事種別
 *
 * <p>
 * アルバム紹介記事、通常の記事、ニュース、イベント情報など、 記事の種類を表現する列挙型です。
 * </p>
 */
public enum ArticleType {
    /** アルバム紹介記事 */
    ALBUM,

    /** 通常記事・ブログ記事 */
    NOTE,

    /** ニュース */
    NEWS,

    /** イベント情報 */
    EVENT,

    /** その他 */
    OTHER;

    /**
     * 外部入力（文字列）から記事種別を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や未知の値は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #valueOf(String)} を使用してください。
     * </p>
     *
     * @param value
     *            記事種別を表す文字列（列挙子名。前後空白は許容）
     * @return 成功時は該当する {@code ArticleType}、失敗時はエラー
     */
    public static Result<ArticleType> fromInput(@Nullable String value) {
        return Policy
                .<String>of(StringUtils::isNotBlank,
                        () -> new ErrorResult("articleType", "記事種別は必須です", "ARTICLE_TYPE_REQUIRED"))
                .verify(value,
                        Function.identity())
                .flatMap(v -> Policy
                        .of(ArticleType::isKnownName,
                                () -> new ErrorResult("articleType", "不正な記事種別です: " + v, "ARTICLE_TYPE_INVALID"))
                        .verify(v, valid -> valueOf(valid.trim())));
    }

    private static boolean isKnownName(@Nullable String value) {
        return value != null && Arrays.stream(values()).anyMatch(t -> t.name().equals(value.trim()));
    }
}
