package com.abservice.application.audio;

import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import java.util.UUID;
import java.util.function.Function;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** 管理APIから受け取る技術IDと編集世代の検証。 */
public final class AudioApiValues {
    private AudioApiValues() {
    }

    public static UUID uuid(@Nullable String value, String field) {
        return uuidPolicy(field)
                .verify(value, UUID::fromString)
                .resolve(ValidationException::new);
    }

    /**
     * @param field
     *            入力の位置
     * @return UUIDの形式だけを検証する規則
     */
    public static Policy<String> uuidPolicy(String field) {
        return Policy.of(
                EntityId::isValidUuid,
                new ErrorResult(
                        field,
                        "有効な値を指定してください",
                        "INVALID_AUDIO_ARGUMENT"));
    }

    public static int revision(@Nullable Integer value) {
        return revisionPolicy()
                .verify(value, Function.identity())
                .resolve(ValidationException::new);
    }

    public static Policy<Integer> revisionPolicy() {
        return Policy.of(
                value -> Optional.ofNullable(value)
                        .filter(revision -> revision >= 0)
                        .filter(revision -> revision < Integer.MAX_VALUE)
                        .isPresent(),
                new ErrorResult(
                        "expectedRevision",
                        "有効な値を指定してください",
                        "INVALID_AUDIO_ARGUMENT"));
    }
}
