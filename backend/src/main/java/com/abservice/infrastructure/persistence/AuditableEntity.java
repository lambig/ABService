package com.abservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 共通監査列を持つエンティティの基底クラス
 * <p>
 * すべてのエンティティは以下の7つの監査列を持つ必要があります:
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
 * @see <a href=
 *      "https://github.com/lambig/ABService/blob/main/docs/CODING_GUIDELINES.md">参考:
 *      ABService CODING_GUIDELINES</a>
 */
@MappedSuperclass
public abstract class AuditableEntity {

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
	 * 例: "album-service", "order-service" など
	 * </p>
	 */
	@Column(name = "created_by_service", length = 255)
	private String createdByService;

	/**
	 * 更新時のアプリケーションサービス名
	 * <p>
	 * 例: "album-service", "order-service" など
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

	// Getters and Setters

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getCreatedByService() {
		return createdByService;
	}

	public void setCreatedByService(String createdByService) {
		this.createdByService = createdByService;
	}

	public String getUpdatedByService() {
		return updatedByService;
	}

	public void setUpdatedByService(String updatedByService) {
		this.updatedByService = updatedByService;
	}

	public String getCreatedByUser() {
		return createdByUser;
	}

	public void setCreatedByUser(String createdByUser) {
		this.createdByUser = createdByUser;
	}

	public String getUpdatedByUser() {
		return updatedByUser;
	}

	public void setUpdatedByUser(String updatedByUser) {
		this.updatedByUser = updatedByUser;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	/**
	 * エンティティに監査情報を設定します（作成時）
	 * <p>
	 * 新規作成時に createdByService と createdByUser を設定します。
	 * </p>
	 *
	 * @param auditInfo
	 *            監査情報
	 */
	public void setCreationAuditInfo(AuditInfo auditInfo) {
		if (auditInfo != null) {
			this.createdByService = auditInfo.serviceName();
			this.createdByUser = auditInfo.userId();
		}
	}

	/**
	 * エンティティに監査情報を設定します（更新時）
	 * <p>
	 * 更新時に updatedByService と updatedByUser を設定します。
	 * </p>
	 *
	 * @param auditInfo
	 *            監査情報
	 */
	public void setUpdateAuditInfo(AuditInfo auditInfo) {
		if (auditInfo != null) {
			this.updatedByService = auditInfo.serviceName();
			this.updatedByUser = auditInfo.userId();
		}
	}
}
