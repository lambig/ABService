package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.hamcrest.Matchers.equalTo;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@QuarkusTest
@ExtendWith(CleanDatabase.class)
class PrivateAudioDisabledRestIntegrationTest {
    @Test
    @DisplayName("既定無効時は管理者でも全音源APIを利用できない")
    void hidesAllEndpointsEvenFromAdmin() {
        final var id = UUID.randomUUID().toString();
        authorized().post("/api/v1/admin/private-audio/registrations").then().statusCode(404)
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
        authorized().get("/api/v1/admin/private-audio/registrations/" + id).then().statusCode(404);
        authorized().get("/api/v1/admin/albums/" + id + "/listening-audio/crossfade").then().statusCode(404);
        authorized().contentType(ContentType.JSON).body(
                Map.of(
                        "audioId",
                        id,
                        "expectedRevision",
                        0))
                .put("/api/v1/admin/albums/" + id + "/listening-audio/crossfade").then().statusCode(404);
    }
}
