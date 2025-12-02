package com.abservice.domain.model.vo.tune;

/**
 * チューンの種類を表す列挙型
 *
 * <p>
 * チューンがトラッド、オリジナル、アレンジのいずれかを表します。
 * </p>
 */
public enum TuneKind {
    /**
     * トラッド（伝統曲）
     */
    TRAD,

    /**
     * オリジナル曲
     */
    ORIGINAL,

    /**
     * アレンジ曲（既存曲のアレンジ版）
     */
    ARRANGEMENT
}
