package com.abservice.domain.model.vo.common;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * マークアップ形式
 *
 * <p>
 * 記事本文やコンテンツのマークアップ形式を表す列挙型です。
 * </p>
 *
 * <p>
 * HTML は持ちません。入力形式は Markdown（およびプレーンテキスト）に一本化し、描画側は生HTMLをパースする経路を
 * 持たないためです（{@code docs/DECISIONS.md} 24）。選べる形式として残すと、公開ページで実行可能なものが
 * 描画される余地が契約に残ります。
 * </p>
 */
public enum MarkupFormat {
    /** プレーンテキスト（マークアップなし） */
    PLAIN_TEXT,

    /** Markdown形式 */
    MARKDOWN;

    /**
     * 列挙子名からマークアップ形式を解決します。未指定（{@code null}）の場合は {@link #PLAIN_TEXT} を既定値とします。
     *
     * <p>
     * 永続化データの復元など、既定値へのフォールバックが妥当な場面で使用します。外部入力の検証には
     * {@link com.abservice.domain.model.vo.common.MarkupContent#fromInput}
     * を使用してください。
     * </p>
     *
     * @param name
     *            マークアップ形式の列挙子名（{@code null} 可）
     * @return 対応するマークアップ形式、{@code name} が {@code null} の場合は {@link #PLAIN_TEXT}
     */
    public static MarkupFormat orDefault(@Nullable String name) {
        return Optional.ofNullable(name)
                .map(MarkupFormat::valueOf)
                .orElse(PLAIN_TEXT);
    }
}
