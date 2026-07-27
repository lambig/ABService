package com.abservice;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.Map;

@Path("/api/v1/health")
public class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        return Response.ok(
                Map.<String, Object>of(
                        "status",
                        "UP",
                        "timestamp",
                        LocalDateTime.now(),
                        "service",
                        "ABService Backend",
                        "version",
                        "1.0.0-SNAPSHOT"))
                .build();
    }
}
