package com.abservice.presentation.rest.audio;

import com.abservice.application.audio.ListeningAuthorizations;
import com.abservice.presentation.rest.audio.response.ListeningAuthorizationResponse;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.openapi.MayThrottle;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jboss.resteasy.reactive.RestResponse;
import org.jspecify.annotations.Nullable;

/**
 * Only an authenticated administrator may resolve a short code and decide its
 * request.
 */
@Path("/api/v1/admin/listening-authorizations")
@RolesAllowed(SecurityRoles.ADMIN)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor
public class ListeningAuthorizationCommandResource {
    private final ListeningAuthorizations authorizations;

    @POST
    @Path("/resolve")
    @MayThrottle
    @Executes(ListeningAuthorizations.class)
    public Uni<RestResponse<ListeningAuthorizationResponse>> resolve(
            @Nullable ResolveListeningAuthorizationRequest input) {
        return authorizations
                .resolve(
                        Optional.ofNullable(input)
                                .map(ResolveListeningAuthorizationRequest::code)
                                .orElse(null))
                .map(ListeningAuthorizationResponse::of)
                .map(ListeningAuthorizationResource::response);
    }

    @POST
    @Path("/{requestId}/decision")
    @Executes(ListeningAuthorizations.class)
    public Uni<RestResponse<ListeningAuthorizationResponse>> decide(@PathParam("requestId") String id,

            @Nullable DecideListeningAuthorizationRequest input) {
        return authorizations
                .decide(
                        id,

                        Optional.ofNullable(input)
                                .map(DecideListeningAuthorizationRequest::approve)
                                .orElse(null),

                        Optional.ofNullable(input)
                                .map(DecideListeningAuthorizationRequest::validDays)
                                .orElse(null))
                .map(ListeningAuthorizationResponse::of)
                .map(ListeningAuthorizationResource::response);
    }

    public record ResolveListeningAuthorizationRequest(@Nullable String code) {
        @Override
        public String toString() {
            return "ResolveListeningAuthorizationRequest[<redacted>]";
        }
    }
    public record DecideListeningAuthorizationRequest(@Nullable Boolean approve,
            @Nullable Integer validDays) {
    }
}
