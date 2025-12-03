package com.abservice.domain.model.vo.article;

import com.abservice.domain.model.vo.ValueObject;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

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
