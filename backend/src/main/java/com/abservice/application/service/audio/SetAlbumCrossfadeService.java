package com.abservice.application.service.audio;

import com.abservice.application.audio.AudioApiValues;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.ConflictingEditException;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.AlbumAudioAssignments;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.service.AlbumAccessService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/** 公開Albumの編集世代とは独立した、試聴用クロスフェードの条件付き関連付け。 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class SetAlbumCrossfadeService
        implements
            CommandService<SetAlbumCrossfadeService.Input, SetAlbumCrossfadeService.Output> {
    private final PrivateAudioAccess access;
    private final AlbumAccessService albums;
    private final PrivateAudioRegistrations registrations;
    private final AlbumAudioAssignments assignments;

    @WithTransaction
    @Override
    public Uni<Output> execute(Input input) {
        return Uni.createFrom().item(() -> {
            access.requireEnabled();
            return input.validated();
        }).call(valid -> albums.findExistingAndClaimReference(Album.Id.of(valid.albumId())))
                .chain(this::assign);
    }

    private Uni<Output> assign(Validated valid) {
        return registrations.find(valid.audioId())
                .map(row -> row.orElseThrow(() -> EntityNotFoundException.of("Audio", valid.audioId())))
                .map(SetAlbumCrossfadeService::requireConfirmed)
                .chain(
                        audio -> assignments.replaceCrossfade(
                                valid.albumId(),
                                audio,
                                valid.revision()))
                .map(
                        changed -> changed
                                ? new Output(
                                        valid.albumId(),
                                        valid.audioId(),
                                        valid.revision() + 1)
                                : conflict());
    }

    private static UUID requireConfirmed(PrivateAudioRegistration registration) {
        return switch (registration.state()) {
            case PrivateAudioRegistration.Confirmed _ -> registration.id();
            default -> throw new BusinessRuleViolationException("確定済みの音源だけを関連付けられます");
        };
    }

    private static Output conflict() {
        throw new ConflictingEditException("クロスフェードの関連付けが更新されています。再読込してください");
    }

    public record Input(@Nullable String albumId, @Nullable String audioId,
            @Nullable Integer expectedRevision) implements CommandService.Input {
        private Validated validated() {
            return new Validated(AudioApiValues.uuid(albumId, "albumId").toString(),
                    AudioApiValues.uuid(audioId, "audioId"), AudioApiValues.revision(expectedRevision));
        }
    }

    private record Validated(String albumId, UUID audioId, int revision) {
    }

    public record Output(String albumId, UUID audioId, int revision) implements CommandService.Output {
    }
}
