package com.abservice.application.service.audio;

import com.abservice.application.audio.AudioApiValues;
import com.abservice.application.audio.AudioRegistrationView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.PrivateAudioOperations;
import com.abservice.application.port.AudioOperationConflictException;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/** 確定済みは冪等応答し、検査済み・放棄済みは非公開実体を照合して復旧する。 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class RecoverAudioService implements CommandService<RecoverAudioService.Input, RecoverAudioService.Output> {
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
                .call(this::recover)
                .chain(row -> registrations.find(row.id()))
                .map(row -> new Output(AudioRegistrationView.of(row.orElseThrow())))
                .onFailure(AudioOperationConflictException.class)
                .transform(cause -> new BusinessRuleViolationException("音源の確定条件または実行枠が競合しています", cause));
    }

    private Uni<Void> recover(PrivateAudioRegistration row) {
        return switch (row.state()) {
            case PrivateAudioRegistration.Confirmed _ -> Uni.createFrom().voidItem();
            case PrivateAudioRegistration.Inspected _, PrivateAudioRegistration.Abandoned _ ->
                operations.recover(row.id()).replaceWithVoid();
            default -> Uni.createFrom().failure(new BusinessRuleViolationException("音源は復旧可能な状態ではありません"));
        };
    }

    public record Input(String audioId) implements CommandService.Input {
    }
    public record Output(AudioRegistrationView registration) implements CommandService.Output {
    }
}
