package com.abservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Optional;

/**
 * 共通監査列を持つテーブルレコードの基底クラス
 * <p>
 * すべてのテーブルレコードは以下の7つの監査列を持つ必要があります:
 * <ul>
 * <li>created_at: レコード作成日時</li>
 * <li>updated_at: レコード最終更新日時</li>
 * <li>created_by_service: 作成時のアプリケーションサービス名</li>
 * <li>updated_by_service: 更新時のアプリケーションサービス名</li>
 * <li>created_by_user: 作成者ユーザーID（外部サービスのユーザーID）</li>
 * <li>updated_by_user: 更新者ユーザーID（外部サービスのユーザーID）</li>
 * <li>version: 楽観ロック用バージョン番号</li>
 * </ul>
 * </p>
 *
 * <p>
 * 自己型ジェネリクス（CRTP、{@link com.abservice.domain.model.DomainObject}と同型）で、
 * setterはサブクラス自身の型（{@code T}）を返す。サブクラス側の{@code @Accessors(chain = true)}
 * によるchainable setterと戻り値型が一致し、継承元・サブクラス自身のsetter呼び出しを
 * 呼び出し順によらず1本のfluentチェーンで連結できる。
 * </p>
 *
 * @param <T>
 *            サブクラス自身の型
 */
@MappedSuperclass
public abstract class AuditableTableRecord<T extends AuditableTableRecord<T>> {

    /**
     * レコード作成日時
     * <p>
     * データベースのDEFAULT値により自動設定されるため、 アプリケーションコードで明示的に設定する必要はありません。
     * </p>
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * レコード最終更新日時
     * <p>
     * {@link #preUpdate()} メソッドにより更新時に自動更新されます。
     * </p>
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * 作成時のアプリケーションサービス名
     * <p>
     * 例: "album-service", "article-service" など
     * </p>
     */
    @Column(name = "created_by_service", length = 255)
    private String createdByService;

    /**
     * 更新時のアプリケーションサービス名
     * <p>
     * 例: "album-service", "article-service" など
     * </p>
     */
    @Column(name = "updated_by_service", length = 255)
    private String updatedByService;

    /**
     * 作成者ユーザーID（外部サービスのユーザーID）
     * <p>
     * Cognitoなどの外部認証サービスから取得したユーザーIDを格納します。
     * </p>
     */
    @Column(name = "created_by_user", length = 255)
    private String createdByUser;

    /**
     * 更新者ユーザーID（外部サービスのユーザーID）
     * <p>
     * Cognitoなどの外部認証サービスから取得したユーザーIDを格納します。
     * </p>
     */
    @Column(name = "updated_by_user", length = 255)
    private String updatedByUser;

    /**
     * 楽観ロック用バージョン番号
     * <p>
     * JPAにより自動的にインクリメントされ、楽観的ロック制御に使用されます。
     * </p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    /**
     * エンティティ更新前の処理
     * <p>
     * {@code updated_at} を現在時刻に更新します。
     * </p>
     */
    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 自分自身を{@code T}として返す（CRTPのキャスト集約点）。
     *
     * @return {@code T}にキャストした自分自身
     */
    @SuppressWarnings("unchecked") // CRTP: 宣言側でT extends AuditableTableRecord<T>のため実行時安全
    private T self() {
        return (T) this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public T setCreatedAt(Instant value) {
        this.createdAt = value;
        return self();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public T setUpdatedAt(Instant value) {
        this.updatedAt = value;
        return self();
    }

    public String getCreatedByService() {
        return createdByService;
    }

    public T setCreatedByService(String value) {
        this.createdByService = value;
        return self();
    }

    public String getUpdatedByService() {
        return updatedByService;
    }

    public T setUpdatedByService(String value) {
        this.updatedByService = value;
        return self();
    }

    public String getCreatedByUser() {
        return createdByUser;
    }

    public T setCreatedByUser(String value) {
        this.createdByUser = value;
        return self();
    }

    public String getUpdatedByUser() {
        return updatedByUser;
    }

    public T setUpdatedByUser(String value) {
        this.updatedByUser = value;
        return self();
    }

    public Integer getVersion() {
        return version;
    }

    public T setVersion(Integer value) {
        this.version = value;
        return self();
    }

    /**
     * エンティティに監査情報を設定します（作成時）
     * <p>
     * 新規作成時に createdByService と createdByUser を設定します。
     * </p>
     *
     * @param auditInfo
     *            監査情報
     * @return 自分自身（chainable）
     */
    public T setCreationAuditInfo(AuditInfo auditInfo) {
        Optional.ofNullable(auditInfo).ifPresent(info -> {
            this.createdByService = info.serviceName();
            this.createdByUser = info.userId();
        });
        return self();
    }

    /**
     * エンティティに監査情報を設定します（更新時）
     * <p>
     * 更新時に updatedByService と updatedByUser を設定します。
     * </p>
     *
     * @param auditInfo
     *            監査情報
     * @return 自分自身（chainable）
     */
    public T setUpdateAuditInfo(AuditInfo auditInfo) {
        Optional.ofNullable(auditInfo).ifPresent(info -> {
            this.updatedByService = info.serviceName();
            this.updatedByUser = info.userId();
        });
        return self();
    }
}
