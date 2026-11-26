package com.abservice.presentation.rest.openapi;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.Optional;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * Keeps throttled state response bodies aligned with the generated success
 * schema.
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class ThrottledResponseFilter implements OASFilter {
    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        DeclaredEndpoints.declaring(MayThrottle.class)
                .forEach(
                        endpoint -> add(
                                Optional.ofNullable(
                                        openAPI.getPaths()
                                                .getPathItem(endpoint.path())
                                                .getOperations()
                                                .get(endpoint.httpMethod()))
                                        .map(Operation::getResponses)
                                        .orElseThrow(
                                                () -> new IllegalStateException(
                                                        "Missing throttled operation: " + endpoint))));
    }

    // MODEL-MUTATION: OpenAPI filters update the generated document in place.
    private static void add(APIResponses responses) {
        responses.addAPIResponse(
                "429",
                OASFactory.createAPIResponse()
                        .description("回数制限。Retry-After の秒数後に再試行する")
                        .content(responses.getAPIResponse("200").getContent())
                        .addHeader(
                                "Retry-After",
                                OASFactory.createHeader()
                                        .required(true)
                                        .schema(
                                                OASFactory.createSchema()
                                                        .addType(Schema.SchemaType.INTEGER))));
    }
}
