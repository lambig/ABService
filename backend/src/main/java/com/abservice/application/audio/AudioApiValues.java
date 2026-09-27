package com.abservice.application.audio;

import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.EntityId;
import com.abservice.lib.ErrorResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 管理APIから受け取る技術IDと編集世代の検証。 */
public final class AudioApiValues {
    private AudioApiValues() {
    }

    public static UUID uuid(@Nullable String value, String field) {
        return Optional.ofNullable(value)
                .filter(EntityId::isValidUuid)
                .map(UUID::fromString)
                .orElseThrow(() -> invalid(field));
    }

    public static int revision(@Nullable Integer value) {
        return Optional.ofNullable(value)
                .filter(revision -> revision >= 0)
                .filter(revision -> revision < Integer.MAX_VALUE)
                .orElseThrow(() -> invalid("expectedRevision"));
    }

    private static ValidationException invalid(String field) {
        return new ValidationException(List.of(
                new ErrorResult(
                        field,
                        "有効な値を指定してください",
                        "INVALID_AUDIO_ARGUMENT")));
    }
}
