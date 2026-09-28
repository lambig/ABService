package com.abservice.presentation.rest.audio;

import static com.abservice.lib.Iterables.toList;

import com.abservice.application.query.audio.ListListeningDevicesService;
import com.abservice.presentation.rest.audio.response.ListeningDeviceListResponse;
import com.abservice.presentation.rest.audio.response.ListeningDeviceResponse;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;

@Path("/api/v1/admin/listening-devices")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class ListeningDeviceAdminQueryResource {
    private final ListListeningDevicesService list;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(ListListeningDevicesService.class)
    public Uni<ListeningDeviceListResponse> list() {
        return list.query(new ListListeningDevicesService.Query())
                .map(ListListeningDevicesService.Result::devices)
                .map(toList(ListeningDeviceResponse::of))
                .map(ListeningDeviceListResponse::new);
    }
}
