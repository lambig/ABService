package com.abservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.port.ListeningDevices;
import com.abservice.application.port.PrivateAudioOperations;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.service.audio.ReceiveAudioService;
import com.abservice.application.service.audio.CreateAudioRegistrationService;
import com.abservice.application.service.audio.IssueListeningDeviceService;
import com.abservice.application.service.audio.SetAlbumCrossfadeService;
import com.abservice.application.port.AlbumAudioAssignments;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.application.service.audio.RecoverAudioService;
import com.abservice.application.service.audio.RevokeListeningDeviceService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AudioCommandInputContractTest {
    private static <T> T unused(Class<T> type) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[]{type},
                        (proxy, method, arguments) -> {
                            throw new AssertionError(proxy.getClass()
                                    .getName() + ": " + method
                                    + " " + Arrays.toString(arguments));
                        }));
    }

    static Stream<Arguments> blockedServices() {
        final var access = new PrivateAudioAccess("false");
        final var registrations = unused(PrivateAudioRegistrations.class);
        final var operations = unused(PrivateAudioOperations.class);
        return Stream.of(
                Arguments.of(
                        new CreateAudioRegistrationService(access, registrations)
                                .execute(new CreateAudioRegistrationService.Input())),
                Arguments.of(
                        new IssueListeningDeviceService(access, unused(ListeningDevices.class))
                                .execute(new IssueListeningDeviceService.Input(null, -1))),
                Arguments.of(
                        new SetAlbumCrossfadeService(access, new AlbumAccessService(null), registrations,
                                unused(AlbumAudioAssignments.class))
                                .execute(
                                        new SetAlbumCrossfadeService.Input(
                                                null,
                                                null,
                                                null))),
                Arguments.of(
                        new ReceiveAudioService(
                                access,
                                registrations,
                                operations)
                                .execute(new ReceiveAudioService.Input("bad", () -> {
                                    throw new AssertionError("source opened");
                                }))),
                Arguments.of(
                        new RecoverAudioService(
                                access,
                                registrations,
                                operations)
                                .execute(new RecoverAudioService.Input("bad"))),
                Arguments.of(
                        new RevokeListeningDeviceService(access, unused(ListeningDevices.class))
                                .execute(new RevokeListeningDeviceService.Input(null))));
    }

    @ParameterizedTest
    @MethodSource("blockedServices")
    @DisplayName("無効な機能は入力エラーより先に404となり、I/Oを開始しない")
    void featureGatePrecedesValidation(Uni<?> result) {
        assertThatThrownBy(
                () -> result.await()
                        .indefinitely())
                .isInstanceOf(EntityNotFoundException.class);
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(new ReceiveAudioService.Input("bad", () -> {
                    throw new AssertionError("source opened");
                }), "audioId"),
                Arguments.of(new RecoverAudioService.Input(" "), "audioId"),
                Arguments.of(new RevokeListeningDeviceService.Input(null), "deviceId"),
                Arguments.of(
                        new SetAlbumCrossfadeService.Input(
                                null,
                                null,
                                null),
                        "albumId"),
                Arguments.of(
                        new SetAlbumCrossfadeService.Input(
                                "abcdef01-2345-6789-abcd-ef0123456789",
                                null,
                                null),
                        "audioId"),
                Arguments.of(
                        new SetAlbumCrossfadeService.Input(
                                "abcdef01-2345-6789-abcd-ef0123456789",
                                "abcdef01-2345-6789-abcd-ef0123456789",
                                Integer.MAX_VALUE),
                        "expectedRevision"));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    @DisplayName("音源・端末IDの既存エラー契約を保持し、音源を開かない")
    void preservesTechnicalIdErrors(CommandService.Input<?> input, String field) {
        final var pending = input.asValidated();
        assertThatThrownBy(
                () -> pending.await()
                        .indefinitely())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors())
                                .containsExactly(
                                        new ErrorResult(
                                                field,
                                                "有効な値を指定してください",
                                                "INVALID_AUDIO_ARGUMENT")));
    }

    static Stream<Arguments> invalidDevices() {
        return Stream.of(
                Arguments.of(
                        null,
                        -1,
                        "label"),
                Arguments.of(
                        "valid",
                        0,
                        "validDays"),
                Arguments.of(
                        "valid",
                        91,
                        "validDays"));
    }

    @ParameterizedTest
    @MethodSource("invalidDevices")
    @DisplayName("端末名のエラーを有効期間より先に返す")
    void labelErrorPrecedesValidity(
            String label,
            Integer days,
            String field) {
        assertThatThrownBy(
                () -> new IssueListeningDeviceService.Input(label, days)
                        .asValidated()
                        .await()
                        .indefinitely())
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.errors())
                            .extracting(ErrorResult::field)
                            .containsExactly(field);
                    assertThat(error.errors())
                            .extracting(ErrorResult::code)
                            .containsExactly("INVALID_LISTENING_DEVICE");
                });
    }

    static Stream<CommandService.Input<?>> validInputs() {
        final var id = "ABCDEF01-2345-6789-ABCD-EF0123456789";
        return Stream.of(
                new ReceiveAudioService.Input(id, () -> {
                    throw new AssertionError("source opened");
                }),
                new RecoverAudioService.Input(id),
                new RevokeListeningDeviceService.Input(id),
                new CreateAudioRegistrationService.Input(),
                new IssueListeningDeviceService.Input("  device  ", null),
                new SetAlbumCrossfadeService.Input(
                        id,
                        id,
                        0));
    }

    @ParameterizedTest
    @MethodSource("validInputs")
    @DisplayName("UUIDの大文字表記を含む正常入力を受け入れ、音源を開かない")
    void acceptsValidTechnicalIds(CommandService.Input<?> input) {
        assertThat(
                input.asValidated()
                        .await()
                        .indefinitely())
                .isSameAs(input);
    }
}
