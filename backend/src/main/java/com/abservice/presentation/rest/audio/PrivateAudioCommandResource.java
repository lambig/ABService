package com.abservice.presentation.rest.audio;

import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING;

import com.abservice.application.service.audio.CreateAudioRegistrationService;
import com.abservice.application.service.audio.ReceiveAudioService;
import com.abservice.application.service.audio.RecoverAudioService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import java.util.Optional;
import io.quarkus.arc.ClientProxy;
import com.abservice.presentation.rest.audio.response.AudioRegistrationResponse;
import com.abservice.presentation.rest.CreatedResponses;
import com.abservice.presentation.rest.openapi.CreatesResource;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.github.lambig.textescape.TextEscape;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/v1/admin/private-audio/registrations")
@RolesAllowed(SecurityRoles.ADMIN)
@AllArgsConstructor
public class PrivateAudioCommandResource {
    private final CreateAudioRegistrationService create;
    private final ReceiveAudioService receive;
    private final RecoverAudioService recover;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(CreateAudioRegistrationService.class)
    @CreatesResource
    public Uni<RestResponse<AudioRegistrationResponse>> reserve() {
        return create.execute(new CreateAudioRegistrationService.Input())
                .map(
                        output -> CreatedResponses.at(
                                TextEscape.escape("/api/v1/admin/private-audio/registrations/${id}")
                                        .where("id", output.registration().audioId()).compile(),
                                AudioRegistrationResponse.of(output.registration())));
    }

    /** Entity parameterを宣言せず、認可・受付後にworkerでHTTP入力を開く。 */
    @PUT
    @Path("/{audioId}/content")
    @Consumes("audio/flac")
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(ReceiveAudioService.class)
    @RequestBody(required = true, content = @Content(schema = @Schema(type = STRING, format = "binary")))
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // TRANSPORT-CLOSE: 全失敗で未読入力の接続を閉じる。
    public Uni<AudioRegistrationResponse> upload(@PathParam("audioId") String audioId,
            @Context ContainerRequestContext request, @Context HttpServerRequest transport) {
        transport.pause();
        return receive.execute(new ReceiveAudioService.Input(audioId, ClientProxy.unwrap(request)::getEntityStream))
                .map(output -> AudioRegistrationResponse.of(output.registration()))
                .onFailure().invoke(failure -> closeAfterResponse(transport));
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // TRANSPORT-CLOSE: 通知値によらず未読入力の接続を閉じる。
    private static void closeAfterResponse(HttpServerRequest transport) {
        Optional.of(transport.version())
                .filter(version -> version != HttpVersion.HTTP_2)
                .filter(version -> Boolean.FALSE.equals(transport.response().headWritten()))
                .ifPresent(version -> transport.response().putHeader("Connection", "close"));
        Optional.of(transport.response())
                .filter(response -> Boolean.FALSE.equals(response.headWritten()))
                .ifPresentOrElse(
                        response -> response.endHandler(ignored -> transport.connection().close()),
                        () -> transport.connection().close());
    }

    @POST
    @Path("/{audioId}/confirm")
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(RecoverAudioService.class)
    public Uni<AudioRegistrationResponse> confirm(@PathParam("audioId") String audioId) {
        return recover.execute(new RecoverAudioService.Input(audioId))
                .map(output -> AudioRegistrationResponse.of(output.registration()));
    }
}
