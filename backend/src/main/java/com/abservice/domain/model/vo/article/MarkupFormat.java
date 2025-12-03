package com.abservice.domain.model.vo.article;

/**
 * マークアップ形式
 *
 * <p>
 * 記事本文やコンテンツのマークアップ形式を表す列挙型です。
 * </p>
 */
public enum MarkupFormat {
    /** プレーンテキスト（マークアップなし） */
    PLAIN_TEXT,

    /** Markdown形式 */
    MARKDOWN,

    /** HTML形式 */
    HTML
}
