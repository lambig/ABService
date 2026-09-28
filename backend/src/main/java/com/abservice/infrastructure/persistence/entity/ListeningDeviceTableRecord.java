package com.abservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 端末資格情報の読取用レコード。発行・失効は条件付きSQLを持つ専用アダプタへ限定する。 */
@Entity
@Table(name = "listening_device")
@Getter
@NoArgsConstructor
public class ListeningDeviceTableRecord {
    @Id
    @Column(name = "device_id")
    private UUID deviceId;
    @Column(name = "label", nullable = false, length = 100)
    private String label;
    @Column(name = "token_digest", nullable = false, length = 64)
    private String tokenDigest;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
