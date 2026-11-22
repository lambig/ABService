package com.abservice.application.query.audio;

import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import java.util.UUID;
import com.abservice.application.audio.AudioRegistrationView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.exception.FailureResult;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.query.QueryService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND})
public class GetAudioRegistrationService
        implements
            QueryService<GetAudioRegistrationService.Query, GetAudioRegistrationService.Result> {
    private final PrivateAudioAccess access;
    private final PrivateAudioRegistrations registrations;

    @Override
    public Uni<Result> query(Query query) {
        return Uni.createFrom().item(query)
                .invoke(access::requireEnabled)
                .map(Query::asValidated)
                .chain(registrations::find).map(
                        row -> row
                                .<Result>map(value -> new Result.Found(AudioRegistrationView.of(value)))
                                .orElseGet(Result.NotFound::new));
    }

    public record Query(@Nullable String audioId) implements QueryService.Query {
        UUID asValidated() {
            return Policy.<String>of(
                    EntityId::isValidUuid,
                    new ErrorResult(
                            "audioId",
                            "有効な値を指定してください",
                            "INVALID_AUDIO_ARGUMENT"))
                    .verify(audioId, UUID::fromString)
                    .resolve(ValidationException::new);
        }
    }

    public sealed interface Result extends QueryService.Result {
        record Found(AudioRegistrationView registration) implements Result {
        }
        @FailureResult(Failure.NOT_FOUND)
        record NotFound() implements Result {
        }
    }
}
