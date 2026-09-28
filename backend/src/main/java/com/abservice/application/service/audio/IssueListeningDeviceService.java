package com.abservice.application.service.audio;

import com.abservice.application.audio.ListeningDeviceToken;
import com.abservice.application.audio.ListeningDeviceView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.ListeningDevices;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.EntityId;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 試聴端末の資格情報を発行する。トークン本体はこの応答でだけ渡し、永続化port自身が独立commitする。
 * 有効期間は既定30日、最長90日。利用による延長はなく、再認証は新しい発行で行う。
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class IssueListeningDeviceService
        implements
            CommandService<IssueListeningDeviceService.Input, IssueListeningDeviceService.Output> {
    static final int DEFAULT_VALID_DAYS = 30;
    static final int MAX_VALID_DAYS = 90;
    private static final int MAX_LABEL_LENGTH = 100;

    private final PrivateAudioAccess access;
    private final ListeningDevices devices;

    @Override
    public Uni<Output> execute(Input input) {
        return Uni.createFrom().item(() -> {
            access.requireEnabled();
            return input.validated();
        }).chain(this::issue);
    }

    private Uni<Output> issue(Validated valid) {
        final var id = UUID.fromString(EntityId.generateUuidV7());
        final var token = ListeningDeviceToken.issue();
        return devices.create(
                id,
                valid.label(),
                token.digest(),
                Instant.now().plus(valid.validity()))
                .invoke(
                        created -> Optional.of(created)
                                .filter(Boolean::booleanValue)
                                .orElseThrow(() -> new BusinessRuleViolationException("端末の発行が競合しました")))
                .chain(() -> devices.find(id))
                .map(row -> new Output(ListeningDeviceView.of(row.orElseThrow(), Instant.now()), token));
    }

    public record Input(@Nullable String label, @Nullable Integer validDays) implements CommandService.Input {
        private Validated validated() {
            return new Validated(
                    Optional.ofNullable(label)
                            .map(String::strip)
                            .filter(value -> value.length() >= 1)
                            .filter(value -> value.length() <= MAX_LABEL_LENGTH)
                            .orElseThrow(() -> invalid("label", "1〜100文字の表示名を指定してください")),
                    Duration.ofDays(
                            Optional.ofNullable(validDays)
                                    .orElse(DEFAULT_VALID_DAYS)));
        }
    }

    private record Validated(String label, Duration validity) {
        private Validated {
            Optional.of(validity.toDays())
                    .filter(days -> days >= 1)
                    .filter(days -> days <= MAX_VALID_DAYS)
                    .orElseThrow(() -> invalid("validDays", "有効期間は1〜90日で指定してください"));
        }
    }

    private static ValidationException invalid(String field, String message) {
        return new ValidationException(
                List.of(
                        new ErrorResult(
                                field,
                                message,
                                "INVALID_LISTENING_DEVICE")));
    }

    /** トークンはこの出力でだけ渡す。ログ・一覧・照会には出さない。 */
    public record Output(ListeningDeviceView device, ListeningDeviceToken token) implements CommandService.Output {
    }
}
