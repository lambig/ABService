package com.abservice.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 試聴端末の資格情報の状態。トークン本体は持たず、配布認可・配布内容もこの値では決まらない。
 *
 * @param id
 *            端末の不変ID
 * @param label
 *            運用者向けの表示名
 * @param createdAt
 *            発行時刻
 * @param expiresAt
 *            有効期限。利用による延長はない
 * @param revokedAt
 *            失効時刻。失効していなければnull
 */
public record ListeningDevice(UUID id, String label, Instant createdAt, Instant expiresAt,
        @Nullable Instant revokedAt) {
    public ListeningDevice {
        Objects.requireNonNull(id);
        Objects.requireNonNull(label);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
    }
}
