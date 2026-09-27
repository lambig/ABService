package com.abservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 登録処理の読取用レコード。状態変更は条件付きSQLを持つ専用アダプタへ限定する。 */
@Entity
@Table(name = "private_audio_registration")
@Getter
@NoArgsConstructor
public class PrivateAudioRegistrationTableRecord {
    @Id
    @Column(name = "audio_id")
    private UUID audioId;
    @Column(name = "state", nullable = false, length = 16)
    private String state;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "byte_length")
    private Long byteLength;
    @Column(name = "sha256", length = 64)
    private String sha256;
    @Column(name = "sample_rate")
    private Integer sampleRate;
    @Column(name = "channels")
    private Integer channels;
    @Column(name = "bits_per_sample")
    private Integer bitsPerSample;
    @Column(name = "total_samples")
    private Long totalSamples;
}
