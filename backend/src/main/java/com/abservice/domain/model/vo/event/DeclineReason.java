package com.abservice.domain.model.vo.event;

/**
 * イベント不参加理由
 *
 * <p>
 * イベントに参加できない理由を表すEnumです。
 * </p>
 */
public enum DeclineReason {
    /**
     * 抽選で落選
     */
    NOT_SELECTED("落選"),

    /**
     * ユーザーによる参加キャンセル
     */
    CANCELLED_BY_USER("キャンセル"),

    /**
     * イベント主催者による中止
     */
    EVENT_CANCELLED("イベント中止");

    /** 表示名 */
    private final String displayName;

    DeclineReason(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 表示名を取得
     *
     * @return 表示名
     */
    public String displayName() {
        return displayName;
    }
}
