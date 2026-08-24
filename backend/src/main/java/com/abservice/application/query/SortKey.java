package com.abservice.application.query;

import java.util.Set;

/**
 * 一覧照会で選べる並び順のキー
 *
 * <p>
 * 集約ごとに選べるキーが異なるため、集約ごとの列挙型が本インタフェースを実装する。外部（クエリパラメータ）の綴りと 内部のプロパティ名を分離し、要求元
 * （{@link Audience}）ごとに使えるキーを閉じる。
 * </p>
 */
public interface SortKey {

    /**
     * @return クエリパラメータで指定する値
     */
    String parameterValue();

    /**
     * @return 並べる対象のプロパティ名
     */
    String property();

    /**
     * @return 向きが指定されなかったときに使う向き
     */
    SortDirection defaultDirection();

    /**
     * @return このキーを使える要求元
     */
    Set<Audience> audiences();
}
