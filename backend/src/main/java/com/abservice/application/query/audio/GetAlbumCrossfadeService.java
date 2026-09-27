package com.abservice.application.query.audio;

import com.abservice.application.audio.AudioApiValues;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.exception.FailureResult;
import com.abservice.application.port.AlbumAudioAssignments;
import com.abservice.infrastructure.persistence.datasource.Visibility;
import com.abservice.application.query.QueryService;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND})
public class GetAlbumCrossfadeService
        implements
            QueryService<GetAlbumCrossfadeService.Query, GetAlbumCrossfadeService.Result> {
    private final PrivateAudioAccess access;
    private final AlbumDataSource albums;
    private final AlbumAudioAssignments assignments;

    @WithSession
    @Override
    public Uni<Result> query(Query query) {
        return Uni.createFrom().item(() -> {
            access.requireEnabled();
            return AudioApiValues.uuid(query.albumId(), "albumId").toString();
        }).chain(id -> albums.findByDomainId(id, Visibility.ALL))
                .chain(this::result);
    }

    private Uni<Result> result(@Nullable AlbumTableRecord album) {
        return Optional.ofNullable(album)
                .map(this::found)
                .orElseGet(() -> Uni.createFrom().item(new Result.NotFound()));
    }

    private Uni<Result> found(AlbumTableRecord album) {
        return assignments.findCrossfade(album.getDomainId())
                .map(
                        assignment -> assignment
                                .<Result>map(
                                        value -> new Result.Found(
                                                album.getDomainId(),
                                                value.audioId(),
                                                value.revision()))
                                .orElseGet(
                                        () -> new Result.Found(
                                                album.getDomainId(),
                                                null,
                                                0)));
    }

    public record Query(@Nullable String albumId) implements QueryService.Query {
    }

    public sealed interface Result extends QueryService.Result {
        record Found(String albumId, @Nullable UUID audioId, int revision) implements Result {
        }
        @FailureResult(Failure.NOT_FOUND)
        record NotFound() implements Result {
        }
    }
}
