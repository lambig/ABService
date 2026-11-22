package com.abservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.service.album.DeleteAlbumInput;
import com.abservice.application.service.album.PublishAlbumInput;
import com.abservice.application.service.album.UnpublishAlbumInput;
import com.abservice.application.service.article.DeleteArticleInput;
import com.abservice.application.service.article.PublishArticleInput;
import com.abservice.application.service.article.RemoveArticleAlbumInput;
import com.abservice.application.service.article.SetArticleAlbumInput;
import com.abservice.application.service.article.UnpublishArticleInput;
import com.abservice.application.service.tune.DeleteTuneInput;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommandInputContractTest {
    private static final String ID = "01900000-0000-7000-8000-000000000001";

    private record Probe(Supplier<Policy<Probe>> rules) implements CommandService.Input<Probe> {
        @Override
        public Policy<Probe> policy() {
            return rules.get();
        }
    }

    private record RejectedInput(List<ErrorResult> errors) implements CommandService.Input<RejectedInput> {
        @Override
        public Policy<RejectedInput> policy() {
            return new InputPolicy<>(input -> Result.failure(input.errors()));
        }
    }

    private record UnexpectedInput(RuntimeException cause) implements CommandService.Input<UnexpectedInput> {
        @Override
        public Policy<UnexpectedInput> policy() {
            return new InputPolicy<>(input -> {
                throw input.cause();
            });
        }
    }

    @Test
    @DisplayName("Policyの取得と検証を購読まで遅らせ、再購読では再評価する")
    void evaluatesOnEachSubscription() {
        final var calls = new AtomicInteger();
        final var input = new Probe(() -> {
            calls.incrementAndGet();
            return new InputPolicy<>(_ -> Result.success(true));
        });
        final var pending = input.asValidated();
        assertThat(calls.get()).isZero();
        assertThat(pending.await().indefinitely()).isSameAs(input);
        assertThat(pending.await().indefinitely()).isSameAs(input);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("位置・message・code・順序・重複を保ってValidationExceptionへ変換する")
    void retainsErrorsAndSkipsConstruction() {
        final var first = new ErrorResult(
                "first",
                "required",
                "FIRST");
        final var second = new ErrorResult(
                "",
                "whole input",
                "SECOND");
        final var expected = List.of(
                first,
                second,
                first);
        final var constructed = new AtomicInteger();
        final var policy = new InputPolicy<List<ErrorResult>>(Result::failure);
        assertThat(policy.verify(expected, value -> constructed.addAndGet(value.size())).errors())
                .containsExactlyElementsOf(expected);
        assertThat(constructed.get()).isZero();
        final var input = new RejectedInput(expected);
        final var pending = input.asValidated();
        assertThatThrownBy(() -> pending.await().indefinitely())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors()).containsExactlyElementsOf(expected));
    }

    @Test
    @DisplayName("想定外の例外を入力エラーへ変換しない")
    void preservesUnexpectedFailure() {
        final var cause = new IllegalStateException("unexpected");
        final var input = new UnexpectedInput(cause);
        final var pending = input.asValidated();
        assertThatThrownBy(() -> pending.await().indefinitely()).isSameAs(cause);
    }

    @ParameterizedTest
    @MethodSource("validInputs")
    @DisplayName("IDを持つ入力は正常値で自身を返す")
    void returnsValidatedInput(CommandService.Input<?> input) {
        assertThat(input.asValidated().await().indefinitely()).isSameAs(input);
    }

    static Stream<CommandService.Input<?>> validInputs() {
        return Stream.of(
                new PublishArticleInput(ID),
                new UnpublishArticleInput(ID),
                new PublishAlbumInput(ID),
                new UnpublishAlbumInput(ID),
                new DeleteArticleInput(ID),
                new DeleteAlbumInput(ID),
                new DeleteTuneInput(ID),
                new SetArticleAlbumInput(
                        ID,
                        ID,
                        0),
                new RemoveArticleAlbumInput(ID, 0));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    @DisplayName("null・空白・不正IDのエラー位置と複数エラーの順序を保つ")
    void rejectsInvalidInput(CommandService.Input<?> input, List<String> fields) {
        final var pending = input.asValidated();
        assertThatThrownBy(() -> pending.await().indefinitely())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors()).extracting(ErrorResult::field)
                                .containsExactlyElementsOf(fields));
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(new PublishArticleInput(null), List.of("articleId", "articleId")),
                Arguments.of(new UnpublishArticleInput(" "), List.of("articleId", "articleId")),
                Arguments.of(new PublishAlbumInput("bad"), List.of("albumId")),
                Arguments.of(new UnpublishAlbumInput(null), List.of("albumId", "albumId")),
                Arguments.of(new DeleteArticleInput("bad"), List.of("articleId")),
                Arguments.of(new DeleteAlbumInput(null), List.of("albumId", "albumId")),
                Arguments.of(new DeleteTuneInput(" "), List.of("tuneId", "tuneId")),
                Arguments.of(
                        new SetArticleAlbumInput(
                                null,
                                "bad",
                                null),
                        List.of(
                                "articleId",
                                "articleId",
                                "albumId",
                                "expectedRevision")),
                Arguments.of(
                        new RemoveArticleAlbumInput(null, null),
                        List.of(
                                "articleId",
                                "articleId",
                                "expectedRevision")));
    }

    @Test
    @DisplayName("Policyは取得元ではなく渡された入力を検証する")
    void validatesPolicyArgument() {
        final var policy = new SetArticleAlbumInput(
                ID,
                ID,
                0).policy();
        assertThat(
                policy.verify(
                        new SetArticleAlbumInput(
                                null,
                                null,
                                null),
                        Function.identity()).errors())
                .extracting(ErrorResult::field)
                .containsExactly(
                        "articleId",
                        "articleId",
                        "albumId",
                        "albumId",
                        "expectedRevision");
    }
}
