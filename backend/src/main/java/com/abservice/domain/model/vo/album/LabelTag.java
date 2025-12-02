package com.abservice.domain.model.vo.album;

/**
 * アルバムのラベルタグを表す列挙型
 *
 * <p>
 * お品書き等で使用するアルバムの分類ラベルです。
 * </p>
 */
public enum LabelTag {
    /**
     * 新譜
     */
    NEW,

    /**
     * ベストアルバム
     */
    BEST_OF,

    /**
     * コンピレーション
     */
    COMPILATION,

    /**
     * コラボレーション
     */
    COLLAB,

    /**
     * その他
     */
    OTHER
}
