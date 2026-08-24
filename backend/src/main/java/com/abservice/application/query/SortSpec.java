package com.abservice.application.query;

/**
 * 解決済みの並び順（並べる対象のプロパティと向き）
 *
 * <p>
 * 同値のときの順序を固定するため、実際の並びには常に {@link #DEFAULT_PROPERTY} をタイブレークとして加える（組み立ては読み取り側の
 * DataSource が行う）。{@code domainId} は UUIDv7 で一意かつ時系列順のため、タイブレークと既定の並びを兼ねられる。
 * </p>
 *
 * @param property
 *            並べる対象のプロパティ名
 * @param direction
 *            並び順の向き
 */
public record SortSpec(String property, SortDirection direction) {

    /** 並び順が指定されなかったときに使うプロパティ（タイブレークにも使う） */
    public static final String DEFAULT_PROPERTY = "domainId";

    /**
     * 並び順が指定されなかったときの既定（登録の新しい順）。
     *
     * @return 既定の並び順
     */
    public static SortSpec defaultOrder() {
        return new SortSpec(DEFAULT_PROPERTY, SortDirection.DESC);
    }
}
