package com.abservice.application.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("試聴端末トークンの形式とdigest")
class ListeningDeviceTokenTest {

    @Test
    @DisplayName("発行したトークンは接頭辞付きの256bit hexで毎回異なる")
    void issuesDistinctWellFormedTokens() {
        final var first = ListeningDeviceToken.issue();
        final var second = ListeningDeviceToken.issue();
        assertThat(first.value()).matches("^abs_device_[0-9a-f]{64}$");
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(ListeningDeviceToken.isWellFormed(first.value())).isTrue();
    }

    @Test
    @DisplayName("digestはSHA-256の小文字hexで本体を復元できない")
    void digestIsSha256Hex() {
        final var token = ListeningDeviceToken.issue();
        assertThat(token.digest()).matches("^[0-9a-f]{64}$").isEqualTo(ListeningDeviceToken.digestOf(token.value()))
                .isNotEqualTo(token.value());
        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test
    @DisplayName("接頭辞や長さが違う値は端末トークンとして扱わない")
    void rejectsMalformedValues() {
        assertThat(ListeningDeviceToken.isWellFormed("abs_session_" + "a".repeat(64))).isFalse();
        assertThat(ListeningDeviceToken.isWellFormed("abs_device_" + "a".repeat(63))).isFalse();
        assertThat(ListeningDeviceToken.isWellFormed("abs_device_" + "a".repeat(65))).isFalse();
        assertThat(ListeningDeviceToken.isWellFormed("")).isFalse();
        assertThat(ListeningDeviceToken.isWellFormed("abs_device_" + "x".repeat(64))).isFalse();
        assertThat(ListeningDeviceToken.isWellFormed("abs_device_" + "A".repeat(64))).isFalse();
        assertThatThrownBy(() -> new ListeningDeviceToken("abs_device_short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListeningDeviceToken("abs_device_" + "x".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
