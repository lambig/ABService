package com.abservice.domain.model.vo.common;

import static io.github.lambig.funcifextension.predicate.By.having;
import static io.github.lambig.funcifextension.predicate.Predicates.and;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * アーティスト名義 Value Object
 *
 * <p>
 * アーティストの表記名義を表すValue Objectです。 例: "Foo Bar", "Foo Bar feat. Baz"
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public final class ArtistCredit implements ValueObject<ArtistCredit> {
    @NonNull
    private final ArtistCreditName displayName;
    @NonNull
    private final String sortKey;

    @Override
    public boolean equivalentTo(ArtistCredit other) {
        return Optional.ofNullable(other)
                .filter(
                        and(
                                having(ArtistCredit::displayName).that(this.displayName::equivalentTo),
                                having(ArtistCredit::sortKey).thatEqualsTo(this.sortKey)))
                .isPresent();
    }

    /**
     * コンストラクタ
     *
     * @param displayName
     *            表示名（必須）
     * @param sortKey
     *            ソートキー（nullの場合はdisplayNameの値を使用）
     */
    private ArtistCredit(@NonNull ArtistCreditName displayName, @Nullable String sortKey) {
        this.displayName = Policy
                .<ArtistCreditName>of(
                        Objects::nonNull,
                        () -> new ErrorResult("displayName", "Display name cannot be null", "DISPLAY_NAME_REQUIRED"))
                .verify(displayName, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        this.sortKey = Optional.ofNullable(sortKey).orElse(displayName.value());
    }

    /**
     * 表示名のみで生成（ソートキーは表示名と同じ）
     *
     * @param displayName
     *            表示名
     * @return ArtistCredit
     */
    public static @NonNull ArtistCredit of(@NonNull String displayName) {
        return new ArtistCredit(new ArtistCreditName(displayName), null);
    }

    /**
     * 表示名とソートキーを指定して生成
     *
     * @param displayName
     *            表示名
     * @param sortKey
     *            ソートキー
     * @return ArtistCredit
     */
    public static @NonNull ArtistCredit of(@NonNull String displayName, @Nullable String sortKey) {
        return new ArtistCredit(new ArtistCreditName(displayName), sortKey);
    }
}
