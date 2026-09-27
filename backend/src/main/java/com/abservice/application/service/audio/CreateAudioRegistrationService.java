package com.abservice.application.service.audio;

import java.util.Optional;
import com.abservice.application.audio.AudioRegistrationView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.EntityId;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;

/** 15分有効な検査受付を予約する。永続化port自身が独立commitする。 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.NOT_FOUND, Failure.CONFLICT})
public class CreateAudioRegistrationService
        implements
            CommandService<CreateAudioRegistrationService.Input, CreateAudioRegistrationService.Output> {
    private final PrivateAudioAccess access;
    private final PrivateAudioRegistrations registrations;

    @Override
    public Uni<Output> execute(Input input) {
        return Uni.createFrom().item(() -> {
            access.requireEnabled();
            return UUID.fromString(EntityId.generateUuidV7());
        }).call(this::reserve)
                .chain(registrations::find)
                .map(row -> new Output(AudioRegistrationView.of(row.orElseThrow())));
    }

    private Uni<Void> reserve(UUID id) {
        return registrations.create(id, Instant.now().plusSeconds(900))
                .invoke(
                        created -> Optional.of(created)
                                .filter(Boolean::booleanValue)
                                .orElseThrow(() -> new BusinessRuleViolationException("音源の登録予約が競合しました")))
                .replaceWithVoid();
    }

    public record Input() implements CommandService.Input {
    }

    public record Output(AudioRegistrationView registration) implements CommandService.Output {
    }
}
