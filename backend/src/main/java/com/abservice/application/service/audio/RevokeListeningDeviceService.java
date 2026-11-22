package com.abservice.application.service.audio;

import com.abservice.lib.Result;
import com.abservice.application.audio.AudioApiValues;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.ListeningDevice;
import com.abservice.application.port.ListeningDevices;
import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.exception.EntityNotFoundException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/** 端末の資格情報を失効させる。以後の認証を拒み、既に失効済みならべき等に成功する。未存在は失敗。 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND})
public class RevokeListeningDeviceService
        implements
            CommandService<RevokeListeningDeviceService.Input, RevokeListeningDeviceService.Output> {
    private final PrivateAudioAccess access;
    private final ListeningDevices devices;

    @Override
    public Uni<Output> execute(Input input) {
        return Uni.createFrom().item(input)
                .invoke(access::requireEnabled)
                .chain(valid -> valid.asValidated(Input::parse)).call(devices::revoke)
                .chain(this::requireExisting)
                .replaceWith(Output::new);
    }

    private Uni<UUID> requireExisting(UUID id) {
        return devices.find(id)
                .map(
                        row -> row.map(ListeningDevice::id)
                                .orElseThrow(() -> EntityNotFoundException.of("ListeningDevice", id.toString())));
    }

    public record Input(@Nullable String deviceId) implements CommandService.Input<Input> {
        @Override
        public Policy<Input> policy() {
            return new InputPolicy<>(Input::parse);
        }

        private static Result<UUID> parse(Input input) {
            return AudioApiValues.uuidPolicy("deviceId")
                    .verify(input.deviceId(), UUID::fromString);
        }
    }

    public record Output() implements CommandService.Output {
    }
}
