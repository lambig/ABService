package com.abservice.presentation.rest.audio;

import com.abservice.application.query.audio.GetListeningPreparationService;
import com.abservice.presentation.rest.audio.response.ListeningPreparationResponse;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jspecify.annotations.Nullable;

/** 受付枠は同一プロセスの全呼出しで共有する。各要求のDB照会は逐次実行する。 */
@Path("/api/v1/admin/listening-preparation")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class ListeningPreparationAdminQueryResource {
    private final GetListeningPreparationService preparations;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(GetListeningPreparationService.class)
    @Bulkhead(value = 4, waitingTaskQueue = 8)
    @Timeout(10000)
    public Uni<ListeningPreparationResponse> preparation(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("albumId") @Nullable String albumId) {
        return preparations.query(new GetListeningPreparationService.Query(page, albumId))
                .map(ListeningPreparationResponse::of);
    }
}
