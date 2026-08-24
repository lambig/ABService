package com.abservice.application.query;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * 一覧照会の並び順の向き
 *
 * <p>
 * 外部（クエリパラメータ）で使う綴りを {@link #parameterValue()} が持ち、内部の列挙名と分離する。
 * </p>
 */
public enum SortDirection {

    /** 昇順 */
    ASC("asc"),

    /** 降順 */
    DESC("desc");

    private final String parameterValue;

    SortDirection(String parameterValue) {
        this.parameterValue = parameterValue;
    }

    /**
     * @return クエリパラメータで指定する値
     */
    public String parameterValue() {
        return parameterValue;
    }

    /**
     * パラメータ値から向きを解決します。
     *
     * @param value
     *            クエリパラメータの値（大文字小文字は区別しない）
     * @return 対応する向き。該当が無ければ空
     */
    public static Optional<SortDirection> ofParameterValue(String value) {
        return Stream.of(values())
                .filter(direction -> direction.parameterValue.equalsIgnoreCase(value))
                .findFirst();
    }

    /**
     * 指定できる値の一覧（エラーメッセージ用）。
     *
     * @return カンマ区切りのパラメータ値
     */
    public static String acceptedValues() {
        return Stream.of(values())
                .map(SortDirection::parameterValue)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
