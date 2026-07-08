package com.abservice.domain.model.vo.article;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * マークアップコンテンツの値オブジェクト
 *
 * <p>
 * 記事本文などのマークアップ可能なコンテンツを表す値オブジェクトです。 コンテンツのテキストとそのマークアップ形式を保持します。
 * </p>
 * <p>
 * マークアップの解釈（レンダリング）はプレゼンテーション層の責務であり、 このオブジェクトは形式情報を含む生のテキストを保持するのみです。
 * </p>
 *
 * @param content
 *            コンテンツテキスト（non-null、空の場合は空文字列）
 * @param format
 *            マークアップ形式
 */
public record MarkupContent(@NonNull String content, MarkupFormat format) implements ValueObject<MarkupContent> {
    /**
     * コンストラクタ
     *
     * @param content
     *            コンテンツテキスト（non-null）
     * @param format
     *            マークアップ形式
     * @throws IllegalArgumentException
     *             contentまたはformatがnullの場合
     */
    public MarkupContent {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("Markup format cannot be null");
        }
    }

    /**
     * プレーンテキストのコンテンツを作成
     *
     * @param content
     *            コンテンツテキスト
     * @return MarkupContentインスタンス
     */
    public static MarkupContent plainText(@NonNull String content) {
        return new MarkupContent(content, MarkupFormat.PLAIN_TEXT);
    }

    /**
     * Markdownコンテンツを作成
     *
     * @param content
     *            Markdownテキスト（nullの場合は空文字列として扱う）
     * @return MarkupContentインスタンス
     */
    public static MarkupContent markdown(String content) {
        return new MarkupContent(content != null ? content : "", MarkupFormat.MARKDOWN);
    }

    /**
     * HTMLコンテンツを作成
     *
     * @param content
     *            HTMLテキスト（nullの場合は空文字列として扱う）
     * @return MarkupContentインスタンス
     */
    public static MarkupContent html(String content) {
        return new MarkupContent(content != null ? content : "", MarkupFormat.HTML);
    }

    /**
     * 外部入力（文字列）からマークアップコンテンツを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 形式（{@code format}）は {@link MarkupFormat}
     * の列挙子名で指定し、未指定・未知の値は {@code Failure} を返します。 コンテンツ本体が {@code null}
     * の場合は空文字列として扱います（既存の {@link #markdown(String)} 等と同方針）。 信頼できる内部生成には
     * {@link #plainText(String)} などのファクトリを使用してください。
     * </p>
     *
     * @param content
     *            コンテンツテキスト（{@code null} は空文字列として扱う）
     * @param format
     *            マークアップ形式を表す文字列（列挙子名。前後空白は許容）
     * @return 成功時は {@code MarkupContent}、失敗時はエラー
     */
    public static Result<MarkupContent> fromInput(@Nullable String content, @Nullable String format) {
        final String safeContent = content != null ? content : "";
        return Policy
                .of((String v) -> v != null && !v.isBlank(),
                        () -> new ErrorResult("format", "マークアップ形式は必須です", "MARKUP_FORMAT_REQUIRED"))
                .verify(format,
                        Function.identity())
                .flatMap(f -> Policy
                        .of(MarkupContent::isKnownFormat,
                                () -> new ErrorResult("format", "不正なマークアップ形式です: " + f, "MARKUP_FORMAT_INVALID"))
                        .verify(f, valid -> new MarkupContent(safeContent, MarkupFormat.valueOf(valid.trim()))));
    }

    private static boolean isKnownFormat(@Nullable String value) {
        return value != null && Arrays.stream(MarkupFormat.values()).anyMatch(f -> f.name().equals(value.trim()));
    }

    /**
     * コンテンツが空かどうか
     *
     * @return 空の場合true
     */
    public boolean isEmpty() {
        return content.isBlank();
    }

    /**
     * コンテンツの長さ
     *
     * @return 文字数
     */
    public int length() {
        return content.length();
    }

    @Override
    public boolean equivalentTo(MarkupContent other) {
        return Optional.ofNullable(other).map(o -> this.content.equals(o.content) && this.format == o.format)
                .orElse(false);
    }
}
