package com.abservice.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 非公開音源の登録状態。保存実体の有無と、配布認可はこの状態だけでは決まらない。 */
public record PrivateAudioRegistration(UUID id, Instant createdAt, Instant expiresAt, State state) {
    public PrivateAudioRegistration {
        Objects.requireNonNull(id);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        Objects.requireNonNull(state);
    }

    /** 検査前と検査済みの値を分け、検査結果のない確定状態を作らない。 */
    public sealed interface State permits Pending, Inspected, Confirmed, Expired, Abandoned {
    }

    public record Pending() implements State {
    }

    public record Inspected(FlacMetadata metadata) implements State {
        public Inspected {
            Objects.requireNonNull(metadata);
        }
    }

    public record Confirmed(FlacMetadata metadata) implements State {
        public Confirmed {
            Objects.requireNonNull(metadata);
        }
    }

    public record Expired() implements State {
    }

    /** 受付を終了した検査済み登録。遅延保存の照合・明示復旧に検査結果を残す。 */
    public record Abandoned(FlacMetadata metadata) implements State {
        public Abandoned {
            Objects.requireNonNull(metadata);
        }
    }
}
