package com.abservice.presentation.rest.audio;

import com.abservice.application.query.audio.GetAudioRegistrationService;
import com.abservice.presentation.rest.audio.response.AudioRegistrationResponse;
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

@Path("/api/v1/admin/private-audio/registrations")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class PrivateAudioAdminQueryResource {
    private final GetAudioRegistrationService registrations;

    @GET
    @Path("/{audioId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(GetAudioRegistrationService.class)
    public Uni<AudioRegistrationResponse> registration(@PathParam("audioId") String audioId) {
        return registrations.query(new GetAudioRegistrationService.Query(audioId)).map(result -> switch (result) {
            case GetAudioRegistrationService.Result.Found found -> AudioRegistrationResponse.of(found.registration());
            case GetAudioRegistrationService.Result.NotFound _ ->
                throw EntityNotFoundException.of("Audio", audioId);
        });
    }

}
