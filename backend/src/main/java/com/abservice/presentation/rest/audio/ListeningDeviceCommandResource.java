package com.abservice.presentation.rest.audio;

import com.abservice.application.service.audio.IssueListeningDeviceService;
import com.abservice.application.service.audio.RevokeListeningDeviceService;
import com.abservice.presentation.rest.CreatedResponses;
import com.abservice.presentation.rest.audio.response.IssuedListeningDeviceResponse;
import com.abservice.presentation.rest.audio.response.ListeningDeviceResponse;
import com.abservice.presentation.rest.openapi.CreatesResource;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.github.lambig.textescape.TextEscape;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jboss.resteasy.reactive.RestResponse;
import org.jspecify.annotations.Nullable;

/** 試聴端末の資格情報の発行と失効。管理者だけが操作し、端末自身は自分の資格情報を変更できない。 */
@Path("/api/v1/admin/listening-devices")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class ListeningDeviceCommandResource {
    private final IssueListeningDeviceService issue;
    private final RevokeListeningDeviceService revoke;

    /** トークンを含む応答は保存禁止にする。 */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(IssueListeningDeviceService.class)
    @CreatesResource
    public Uni<RestResponse<IssuedListeningDeviceResponse>> issue(@Nullable IssueListeningDeviceRequest request) {
        return issue.execute(
                Optional.ofNullable(request)
                        .map(value -> new IssueListeningDeviceService.Input(value.label(), value.validDays()))
                        .orElseGet(() -> new IssueListeningDeviceService.Input(null, null)))
                .map(
                        output -> CreatedResponses.withoutCaching(
                                TextEscape.escape("/api/v1/admin/listening-devices/${id}")
                                        .where("id", output.device().deviceId()).compile(),
                                new IssuedListeningDeviceResponse(
                                        ListeningDeviceResponse.of(output.device()),
                                        output.token().value())));
    }

    @DELETE
    @Path("/{deviceId}")
    @Executes(RevokeListeningDeviceService.class)
    public Uni<Void> revoke(@PathParam("deviceId") String deviceId) {
        return revoke.execute(new RevokeListeningDeviceService.Input(deviceId)).replaceWithVoid();
    }

    public record IssueListeningDeviceRequest(@Nullable String label, @Nullable Integer validDays) {
    }
}
