package com.abservice.application.service.audio;

import com.abservice.application.audio.AudioApiValues;
import com.abservice.application.audio.AudioRegistrationView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.InvalidFlacException;
import com.abservice.application.port.AudioOperationConflictException;
import com.abservice.application.port.PrivateAudioConflictException;
import com.abservice.application.port.PrivateAudioOperations;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import lombok.AllArgsConstructor;

/** 入力を開く前に受付状態を検査する。検査結果のcommitと保存はportが所有する。 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class ReceiveAudioService implements CommandService<ReceiveAudioService.Input, ReceiveAudioService.Output> {
    private final PrivateAudioAccess access;
    private final PrivateAudioRegistrations registrations;
    private final PrivateAudioOperations operations;

    @Override
    public Uni<Output> execute(Input input) {
        return Uni.createFrom().item(() -> {
            access.requireEnabled();
            return AudioApiValues.uuid(input.audioId(), "audioId");
        }).chain(registrations::find)
                .map(row -> row.orElseThrow(() -> new EntityNotFoundException("音源の登録予約がありません")))
                .invoke(ReceiveAudioService::requirePending)
                .call(row -> operations.ingest(row.id(), input.source()))
                .chain(row -> registrations.find(row.id()))
                .map(row -> new Output(AudioRegistrationView.of(row.orElseThrow())))
                .onFailure(InvalidFlacException.class).transform(
                        failure -> new ValidationException(List.of(
                                new ErrorResult(
                                        "audio",
                                        "受入可能なFLAC音源ではありません",
                                        failure.getMessage()))))
                .onFailure(AudioOperationConflictException.class).transform(ReceiveAudioService::conflict)
                .onFailure(PrivateAudioConflictException.class).transform(ReceiveAudioService::conflict);
    }

    private static BusinessRuleViolationException conflict(Throwable cause) {
        return new BusinessRuleViolationException("音源の登録状態または実行枠が競合しています", cause);
    }

    private static void requirePending(PrivateAudioRegistration row) {
        Optional.of(row)
                .filter(value -> value.state() instanceof PrivateAudioRegistration.Pending)
                .filter(value -> value.expiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleViolationException("音源の受付状態または期限が変更されています"));
    }

    public record Input(String audioId, Callable<InputStream> source) implements CommandService.Input {
    }
    public record Output(AudioRegistrationView registration) implements CommandService.Output {
    }
}
