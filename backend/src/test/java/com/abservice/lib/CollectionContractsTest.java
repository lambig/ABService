package com.abservice.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/** 共通処理の所有先を決める前に保持する内部契約。 */
class CollectionContractsTest {

    @Test
    @DisplayName("片方が欠けた組では照合と写像を評価しない")
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // 非評価を検証するため、入力値に依存せず呼び出しだけを記録する。
    void absentPairDoesNotEvaluateMatchingOrMapping() {
        final var calls = new AtomicInteger();
        assertThat(
                Stream.of(
                        Optionals.<String, String>both(null, "right"),
                        Optionals.<String, String>both("left", null),
                        Optionals.<String, String>both(null, null))
                        .map(pair -> pair.filter(Both.by((left, right) -> calls.incrementAndGet() > 0)))
                        .map(pair -> pair.map(Both.to((left, right) -> calls.incrementAndGet())))
                        .flatMap(Optional::stream))
                .isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("値の組は引数順と値の同値性を保つ")
    void presentPairPreservesArgumentOrderAndValueEquality() {
        assertThat(Optionals.both("left", "right"))
                .contains(new Both<>("left", "right"));
        assertThat(
                Optionals.both("left", "right")
                        .filter(Both.by((left, right) -> left.equals("left") && right.equals("right")))
                        .map(Both.to((left, right) -> left + ":" + right)))
                .contains("left:right");
    }

    @Test
    @DisplayName("空の集約結果も利用側が絞り込むまでは存在する")
    void emptyCollectionRemainsPresentUntilTheCallerFiltersIt() {
        final var collected = Stream.<String>empty()
                .collect(Optionals.optionally(Collectors.toUnmodifiableList()));
        assertThat(collected).contains(List.of());
        assertThat(collected.filter(not(List::isEmpty))).isEmpty();
    }

    @Test
    @DisplayName("Optionalへの集約は後段の順序と終了処理を保つ")
    void optionalCollectorKeepsDownstreamOrderAndFinisher() {
        assertThat(
                Stream.of(
                        "first",
                        "second",
                        "first")
                        .parallel()
                        .collect(Optionals.optionally(Collectors.joining("/"))))
                .contains("first/second/first");
    }

    @Test
    @DisplayName("Iterableの写像は順序と重複を保ち変更不能な独立結果を返す")
    @SuppressWarnings("PMD.ForbiddenMutableCollectionMutation") // 入力と結果の独立性、および結果の変更拒否を検証するための変異操作。
    void iterableMappingKeepsOrderDuplicatesAndAnIndependentUnmodifiableResult() {
        final var source = new ArrayList<>(
                List.of(
                        "one",
                        "two",
                        "one"));
        final Iterable<String> iterable = source::iterator;
        final var result = Iterables.<String, String>toList(value -> value.toUpperCase(Locale.ROOT)).apply(iterable);
        source.clear();
        assertThat(result).containsExactly(
                "ONE",
                "TWO",
                "ONE");
        assertThatThrownBy(() -> result.add("THREE")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("空のIterableは写像を呼ばず写像の例外はそのまま伝える")
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // 非評価と例外の同一性を検証するため、値によらず同じ例外を投げる。
    void emptyIterableDoesNotInvokeMapperAndMapperFailuresPropagate() {
        final var failure = new IllegalArgumentException("mapping failed");
        assertThat(Iterables.<String, String>toList(value -> {
            throw failure;
        }).apply(List.of())).isEmpty();
        assertThatThrownBy(() -> Iterables.<String, String>toList(value -> {
            throw failure;
        }).apply(List.of("one"))).isSameAs(failure);
    }
}
