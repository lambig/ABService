package com.abservice.infrastructure.persistence;

import org.jspecify.annotations.Nullable;

/**
 * 監査情報を保持するデータクラス
 * <p>
 * エンティティの作成・更新時に誰が何のサービスから操作したかを記録するための情報を保持します。
 * </p>
 *
 * <p>
 * 現時点で本型を渡す呼び出し元はありません（{@link AuditableTableRecord} の actor 4列は未設定のまま運用する）。
 * 行ごとに区別できる actor を持つ認証を入れる際の受け口として残しています。理由は{@code docs/DECISIONS.md} 5。
 * </p>
 *
 * @param serviceName
 *            操作を実行したアプリケーションサービス名
 * @param userId
 *            操作を実行した actor の識別子
 */
public record AuditInfo(@Nullable String serviceName, @Nullable String userId) {
    /**
     * サービス名のみを指定してAuditInfoを作成するファクトリメソッド
     *
     * @param serviceName
     *            サービス名
     * @return サービス名のみが設定されたAuditInfo
     */
    public static AuditInfo fromService(String serviceName) {
        return new AuditInfo(serviceName, null);
    }

    /**
     * ユーザーIDのみを指定してAuditInfoを作成するファクトリメソッド
     *
     * @param userId
     *            ユーザーID
     * @return ユーザーIDのみが設定されたAuditInfo
     */
    public static AuditInfo fromUser(String userId) {
        return new AuditInfo(null, userId);
    }
}
