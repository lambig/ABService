package com.abservice.domain.model.vo.article;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 記事タイトルの値オブジェクト
 *
 * <p>
 * 記事のタイトルを表す値オブジェクトです。以下の制約を持ちます：
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は500文字です（{@code article.title} カラムの上限に一致）</li>
 * </ul>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            記事タイトル
 */
public record ArticleTitle(@NonNull String value) implements ValueObject<ArticleTitle> {

    private static final int MAX_LENGTH = 500;

    /**
     * コンストラクタ
     *
     * @param value
     *            記事タイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public ArticleTitle {
        titlePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            記事タイトル
     * @return ArticleTitleインスタンス
     */
    public static @NonNull ArticleTitle of(@NonNull String value) {
        return new ArticleTitle(value);
    }

    /**
     * 外部入力（文字列）から記事タイトルを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            記事タイトルを表す文字列
     * @return 成功時は {@code ArticleTitle}、失敗時はエラー
     */
    public static Result<ArticleTitle> fromInput(@Nullable String value) {
        return titlePolicy().verify(value, ArticleTitle::new);
    }

    private static Policy<String> titlePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "title",
                                "記事タイトルは必須です",
                                "ARTICLE_TITLE_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult(
                                "title",
                                "記事タイトルは" + MAX_LENGTH + "文字以内です",
                                "ARTICLE_TITLE_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(ArticleTitle other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
