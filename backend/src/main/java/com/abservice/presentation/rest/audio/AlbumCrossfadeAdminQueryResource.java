package com.abservice.presentation.rest.audio;

import com.abservice.application.query.audio.GetAlbumCrossfadeService;
import com.abservice.presentation.rest.audio.response.AlbumCrossfadeResponse;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;

@Path("/api/v1/admin/albums/{albumId}/listening-audio/crossfade")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class AlbumCrossfadeAdminQueryResource {
    private final GetAlbumCrossfadeService crossfades;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(GetAlbumCrossfadeService.class)
    public Uni<AlbumCrossfadeResponse> crossfade(@PathParam("albumId") String albumId) {
        return crossfades.query(new GetAlbumCrossfadeService.Query(albumId)).map(result -> switch (result) {
            case GetAlbumCrossfadeService.Result.Found found ->
                new AlbumCrossfadeResponse(
                        found.albumId(),
                        "album-crossfade",
                        found.audioId(),
                        found.revision());
            case GetAlbumCrossfadeService.Result.NotFound _ -> throw EntityNotFoundException.of("Album", albumId);
        });
    }
}
