package com.abservice.infrastructure.persistence;

/**
 * 監査情報を保持するデータクラス
 * <p>
 * エンティティの作成・更新時に誰が何のサービスから操作したかを記録するための情報を保持します。
 * </p>
 *
 * @param serviceName
 *            操作を実行したアプリケーションサービス名（例: "album-service", "article-service"）
 * @param userId
 *            操作を実行したユーザーID（外部サービスのユーザーID、例: Cognito User ID）
 */
public record AuditInfo(String serviceName, String userId) {
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
