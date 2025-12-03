package com.abservice.domain.model.vo.article;

import com.abservice.domain.model.vo.ValueObject;

import java.util.Objects;
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
 *            コンテンツテキスト（nullable: 本文が空の場合もある）
 * @param format
 *            マークアップ形式
 */
public record MarkupContent(String content, MarkupFormat format) implements ValueObject<MarkupContent> {
    /**
     * コンストラクタ
     *
     * @param content
     *            コンテンツテキスト（nullable）
     * @param format
     *            マークアップ形式
     * @throws IllegalArgumentException
     *             形式がnullの場合
     */
    public MarkupContent {
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
    public static MarkupContent plainText(String content) {
        return new MarkupContent(content, MarkupFormat.PLAIN_TEXT);
    }

    /**
     * Markdownコンテンツを作成
     *
     * @param content
     *            Markdownテキスト
     * @return MarkupContentインスタンス
     */
    public static MarkupContent markdown(String content) {
        return new MarkupContent(content, MarkupFormat.MARKDOWN);
    }

    /**
     * HTMLコンテンツを作成
     *
     * @param content
     *            HTMLテキスト
     * @return MarkupContentインスタンス
     */
    public static MarkupContent html(String content) {
        return new MarkupContent(content, MarkupFormat.HTML);
    }

    /**
     * コンテンツが空かどうか
     *
     * @return 空の場合true
     */
    public boolean isEmpty() {
        return content == null || content.isBlank();
    }

    /**
     * コンテンツの長さ
     *
     * @return 文字数（nullの場合は0）
     */
    public int length() {
        return content == null ? 0 : content.length();
    }

    @Override
    public boolean equivalentTo(MarkupContent other) {
        return Optional.ofNullable(other)
                .filter(o -> Objects.equals(this.content, o.content) && this.format == o.format).isPresent();
    }
}
