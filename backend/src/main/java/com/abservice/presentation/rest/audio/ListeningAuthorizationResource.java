package com.abservice.presentation.rest.audio;

import com.abservice.application.audio.ListeningAuthorizations;
import com.abservice.presentation.rest.audio.response.ListeningAuthorizationResponse;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.openapi.MayThrottle;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import java.util.List;
import lombok.AllArgsConstructor;
import org.jboss.resteasy.reactive.RestResponse;
import org.jspecify.annotations.Nullable;

/**
 * Anonymous bootstrap; redemption requires the separately issued high-entropy
 * proof.
 */
@Path("/api/v1/listening/authorizations")
@PermitAll
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor
public class ListeningAuthorizationResource {
    private final ListeningAuthorizations authorizations;

    @POST
    @MayThrottle
    @Executes(ListeningAuthorizations.class)
    public Uni<RestResponse<ListeningAuthorizationResponse>> start(@Nullable StartListeningAuthorizationRequest input) {
        return authorizations
                .start(
                        Optional.ofNullable(input)
                                .map(StartListeningAuthorizationRequest::label)
                                .orElse(null))
                .map(ListeningAuthorizationResponse::started)
                .map(ListeningAuthorizationResource::response);
    }

    @POST
    @Path("/token")
    @MayThrottle
    @Executes(ListeningAuthorizations.class)
    public Uni<RestResponse<ListeningAuthorizationResponse>> poll(@Nullable PollListeningAuthorizationRequest input) {
        return authorizations
                .poll(
                        Optional.ofNullable(input)
                                .map(PollListeningAuthorizationRequest::requestId)
                                .orElse(null),

                        Optional.ofNullable(input)
                                .map(PollListeningAuthorizationRequest::proof)
                                .orElse(null))
                .map(ListeningAuthorizationResponse::polled)
                .map(ListeningAuthorizationResource::response);
    }

    static RestResponse<ListeningAuthorizationResponse> response(ListeningAuthorizationResponse value) {

        return RestResponse.ResponseBuilder.<ListeningAuthorizationResponse>create(
                List.of("RATE_LIMITED", "SLOW_DOWN")
                        .contains(value.state())
                                ? 429
                                : 200)
                .entity(value)
                .header(
                        "Cache-Control",
                        "no-store")
                .header(
                        "Pragma",
                        "no-cache")
                .header(
                        "Retry-After",
                        Integer.toString(value.retryAfterSeconds()))
                .build();
    }

    public record StartListeningAuthorizationRequest(@Nullable String label) {
    }
    public record PollListeningAuthorizationRequest(@Nullable String requestId,
            @Nullable String proof) {
        @Override
        public String toString() {
            return "PollListeningAuthorizationRequest[<redacted>]";
        }
    }
}
