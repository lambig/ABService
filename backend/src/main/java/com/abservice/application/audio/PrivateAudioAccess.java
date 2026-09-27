package com.abservice.application.audio;

import java.util.Optional;
import com.abservice.domain.exception.EntityNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** 管理APIも実行基盤と同じ有効化条件を要求する。認可はHTTP境界が別に検査する。 */
@ApplicationScoped
public class PrivateAudioAccess {
    private final String enabled;

    public PrivateAudioAccess(
            @ConfigProperty(name = "abservice.private-audio.enabled", defaultValue = "false") String enabled) {
        this.enabled = enabled;
    }

    public void requireEnabled() {
        Optional.of(enabled)
                .filter("true"::equals)
                .orElseThrow(() -> new EntityNotFoundException("Private audio is unavailable"));
    }
}
