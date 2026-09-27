package com.abservice.presentation.rest.audio;

import com.abservice.application.service.audio.CreateAudioRegistrationService;
import com.abservice.presentation.rest.audio.response.AudioRegistrationResponse;
import com.abservice.presentation.rest.CreatedResponses;
import com.abservice.presentation.rest.openapi.CreatesResource;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.github.lambig.textescape.TextEscape;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/v1/admin/private-audio/registrations")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class PrivateAudioCommandResource {
    private final CreateAudioRegistrationService create;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(CreateAudioRegistrationService.class)
    @CreatesResource
    public Uni<RestResponse<AudioRegistrationResponse>> reserve() {
        return create.execute(new CreateAudioRegistrationService.Input())
                .map(
                        output -> CreatedResponses.at(
                                TextEscape.escape("/api/v1/admin/private-audio/registrations/${id}")
                                        .where("id", output.registration().audioId()).compile(),
                                AudioRegistrationResponse.of(output.registration())));
    }

}
