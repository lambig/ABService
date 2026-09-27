package com.abservice.application.query.audio;

import com.abservice.application.audio.AudioApiValues;
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
        return Uni.createFrom().item(() -> {
            access.requireEnabled();
            return AudioApiValues.uuid(query.audioId(), "audioId");
        }).chain(registrations::find).map(
                row -> row
                        .<Result>map(value -> new Result.Found(AudioRegistrationView.of(value)))
                        .orElseGet(Result.NotFound::new));
    }

    public record Query(@Nullable String audioId) implements QueryService.Query {
    }

    public sealed interface Result extends QueryService.Result {
        record Found(AudioRegistrationView registration) implements Result {
        }
        @FailureResult(Failure.NOT_FOUND)
        record NotFound() implements Result {
        }
    }
}
