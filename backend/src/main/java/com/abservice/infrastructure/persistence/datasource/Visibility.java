package com.abservice.infrastructure.persistence.datasource;

/**
 * DataSourceの検索メソッドが対象とする公開状態のスコープ
 *
 * <p>
 * 公開向けQuery専用の {@code findPublicBy*}／{@code pagedPublicQuery}
 * のような姉妹メソッドを状態ごとに増殖させる代わりに、既存の検索メソッドへ本enumを引数として渡すことで スコープを切り替える。
 * </p>
 */
public enum Visibility {

    /** 公開・下書きを問わず全件を対象にする */
    ALL,

    /** 公開中のもののみを対象にする */
    PUBLIC_ONLY
}
