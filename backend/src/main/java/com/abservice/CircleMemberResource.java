package com.abservice;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/v1/circle-members")
public class CircleMemberResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllCircleMembers() {
        // モックデータを返す
        List<Map<String, Object>> members = new ArrayList<>();

        Map<String, Object> member1 = new HashMap<>();
        member1.put("id", 1L);
        member1.put("username", "admin");
        member1.put("displayName", "Administrator");
        member1.put("email", "admin@abservice.com");
        member1.put("bio", "System administrator");
        member1.put("isActive", true);
        members.add(member1);

        Map<String, Object> member2 = new HashMap<>();
        member2.put("id", 2L);
        member2.put("username", "john_doe");
        member2.put("displayName", "John Doe");
        member2.put("email", "john.doe@example.com");
        member2.put("bio", "Software developer");
        member2.put("isActive", true);
        members.add(member2);

        return Response.ok(members).build();
    }
}
