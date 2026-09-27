package com.abservice.presentation.rest.audio;

import com.abservice.application.service.audio.SetAlbumCrossfadeService;
import com.abservice.presentation.rest.audio.response.AlbumCrossfadeResponse;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

@Path("/api/v1/admin/albums/{albumId}/listening-audio/crossfade")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class AlbumCrossfadeCommandResource {
    private final SetAlbumCrossfadeService setCrossfade;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(SetAlbumCrossfadeService.class)
    public Uni<AlbumCrossfadeResponse> set(@PathParam("albumId") String albumId,
            @Nullable SetAlbumCrossfadeRequest request) {
        return setCrossfade
                .execute(
                        Optional.ofNullable(request)
                                .map(
                                        value -> new SetAlbumCrossfadeService.Input(
                                                albumId,
                                                value.audioId(),
                                                value.expectedRevision()))
                                .orElseGet(
                                        () -> new SetAlbumCrossfadeService.Input(
                                                albumId,
                                                null,
                                                null)))
                .map(
                        output -> new AlbumCrossfadeResponse(output.albumId(), "album-crossfade", output.audioId(),
                                output.revision()));
    }

    public record SetAlbumCrossfadeRequest(@Nullable String audioId, @Nullable Integer expectedRevision) {
    }
}
