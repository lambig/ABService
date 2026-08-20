package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.lib.ErrorResult;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 公開中状態を表す{@link Publication}
 *
 * @param at
 *            公開日時（non-null）
 */
public record Published(@NonNull BusinessDateTime at) implements Publication {

    /** at必須違反時のエラー */
    private static final ErrorResult PUBLISHED_AT_REQUIRED_ERROR = new ErrorResult(
            "at",
            "Published at cannot be null",
            "PUBLISHED_AT_REQUIRED");

    public Published {
        Policy.<BusinessDateTime>of(
                Objects::nonNull,
                PUBLISHED_AT_REQUIRED_ERROR)
                .verify(at, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    @Override
    public boolean isPublished() {
        return true;
    }

    @Override
    public @NonNull Optional<BusinessDateTime> publishedAt() {
        return Optional.of(at);
    }

    @Override
    public boolean equivalentTo(Publication other) {
        return Optional.ofNullable(other)
                .flatMap(
                        o -> o instanceof Published p
                                ? Optional.of(p)
                                : Optional.<Published>empty())
                .filter(p -> this.at.equivalentTo(p.at))
                .isPresent();
    }
}
