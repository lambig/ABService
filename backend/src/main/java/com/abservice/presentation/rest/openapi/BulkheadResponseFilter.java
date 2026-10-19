package com.abservice.presentation.rest.openapi;

import com.abservice.presentation.rest.exception.ProblemDetail;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.Optional;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.media.Schema;

/** 受付制御の宣言から503と再試行ヘッダーを生成する。 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class BulkheadResponseFilter implements OASFilter {
    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        DeclaredEndpoints.declaring(Bulkhead.class).forEach(
                endpoint -> Optional.ofNullable(
                        openAPI.getPaths()
                                .getPathItem(endpoint.path()).getOperations().get(endpoint.httpMethod()))
                        .map(Operation::getResponses).orElseThrow()
                        .addAPIResponse(
                                "503",
                                OASFactory.createAPIResponse()
                                        .description("受付超過または期限切れ。Retry-Afterの秒数後に再試行できる")
                                        .content(
                                                OASFactory.createContent().addMediaType(
                                                        ProblemDetail.MEDIA_TYPE,
                                                        OASFactory.createMediaType().schema(
                                                                OASFactory.createSchema()
                                                                        .ref("#/components/schemas/ProblemDetail"))))
                                        .addHeader(
                                                "Retry-After",
                                                OASFactory.createHeader().required(true)
                                                        .schema(
                                                                OASFactory.createSchema()
                                                                        .addType(Schema.SchemaType.INTEGER)))));
    }
}
