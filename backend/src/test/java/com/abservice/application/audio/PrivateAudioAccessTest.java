package com.abservice.application.audio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.abservice.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PrivateAudioAccessTest {
    @ParameterizedTest
    @ValueSource(strings = {"false", "", "TRUE", " true", "invalid"})
    @DisplayName("厳密なtrue以外では管理機能を無効に保つ")
    void rejectsOtherValues(String value) {
        assertThatThrownBy(() -> new PrivateAudioAccess(value).requireEnabled())
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("厳密なtrueでだけ管理機能を有効にする")
    void acceptsExactTrue() {
        assertThatCode(() -> new PrivateAudioAccess("true").requireEnabled()).doesNotThrowAnyException();
    }
}
