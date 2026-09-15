package com.abservice.presentation.rest.publication;

import com.abservice.application.query.publication.GetPublicDataGenerationQuery;
import com.abservice.application.query.publication.GetPublicDataGenerationService;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.publication.response.PublicDataGenerationResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.jboss.resteasy.reactive.RestResponse;

/** 公開Queryと同じ認証契約で、内容や管理情報を含まない世代だけを返す。 */
@Path("/api/v1/public-data-generation")
@AllArgsConstructor
public class PublicDataGenerationQueryResource {

    private final GetPublicDataGenerationService service;

    /**
     * @return キャッシュしない保存済み世代
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(GetPublicDataGenerationService.class)
    public Uni<RestResponse<PublicDataGenerationResponse>> get() {
        return service.query(new GetPublicDataGenerationQuery())
                .map(
                        result -> RestResponse.ResponseBuilder.<PublicDataGenerationResponse>ok(
                                new PublicDataGenerationResponse(result.generation()))
                                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                .build());
    }
}
