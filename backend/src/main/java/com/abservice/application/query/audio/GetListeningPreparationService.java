package com.abservice.application.query.audio;

import static io.github.lambig.funcifextension.predicate.Predicates.and;
import static java.util.function.Function.identity;

import com.abservice.application.audio.ListeningDeviceView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.query.QueryService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.policy.Policy;
import com.abservice.infrastructure.persistence.datasource.ListeningPreparationDataSource;
import com.abservice.infrastructure.persistence.datasource.ListeningPreparationRow;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/** 一覧・音源の準備状態・端末を一要求で照会する。準備状態は端末への保存完了を表さない。 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND})
public class GetListeningPreparationService
        implements
            QueryService<GetListeningPreparationService.Query, GetListeningPreparationService.Result> {
    private final PrivateAudioAccess access;
    private final ListeningPreparationDataSource preparations;
    private final ListListeningDevicesService devices;

    @Override
    public Uni<Result> query(Query query) {
        return Uni.createFrom().item(query)
                .invoke(access::requireEnabled)
                .map(Query::asValidated)
                .chain(validated -> preparations.page(validated.page(), validated.albumId().orElse(null)))
                .invoke(page -> requireFocus(query, page))
                .chain(this::withDevices);
    }

    private Uni<Result> withDevices(ListeningPreparationDataSource.Page page) {
        return devices.query(new ListListeningDevicesService.Query())
                .map(list -> result(page, list.devices()));
    }

    private static Result result(ListeningPreparationDataSource.Page page, List<ListeningDeviceView> devices) {
        return new Result(
                albums(page.items()),
                page.page(),
                page.totalPages(),
                Optional.ofNullable(page.focused())
                        .map(Album::of)
                        .orElse(null),
                devices);
    }

    private static List<Album> albums(List<ListeningPreparationRow> rows) {
        return rows.stream().map(Album::of).toList();
    }

    private static void requireFocus(Query query, ListeningPreparationDataSource.Page page) {
        Optional.ofNullable(query.albumId()).ifPresent(
                id -> Optional.ofNullable(page.focused())
                        .orElseThrow(() -> EntityNotFoundException.of("Album", id)));
    }

    public record Query(int page, @Nullable String albumId) implements QueryService.Query {
        /** 入力だけを検証する。既存契約に合わせ、ページの失敗をIDより先に返す。 */
        ValidatedQuery asValidated() {
            final var optionalId = Optional.ofNullable(albumId);
            return Policy.<Integer>of(
                    and(
                            value -> value >= 0,
                            value -> value <= Integer.MAX_VALUE / ListeningPreparationDataSource.PAGE_SIZE),
                    new ErrorResult(
                            "page",
                            "有効なページを指定してください",
                            "INVALID_PAGE"))
                    .verify(page, identity())
                    .flatMap(
                            validPage -> Policy.<Optional<String>>of(
                                    value -> value.map(EntityId::isValidUuid)
                                            .orElse(true),
                                    new ErrorResult(
                                            "albumId",
                                            "有効な値を指定してください",
                                            "INVALID_AUDIO_ARGUMENT"))
                                    .verify(
                                            optionalId,
                                            id -> new ValidatedQuery(
                                                    validPage,
                                                    id.map(UUID::fromString)
                                                            .map(UUID::toString))))
                    .resolve(ValidationException::new);
        }
    }

    record ValidatedQuery(int page, Optional<String> albumId) {
    }

    public record Album(String albumId, String title, @Nullable Instant publishedAt, boolean audioConfirmed) {
        static Album of(ListeningPreparationRow row) {
            return new Album(
                    row.albumId(),
                    row.title(),
                    row.publishedAt(),
                    "CONFIRMED".equals(row.audioState()));
        }
    }

    public record Result(List<Album> items, int page, int totalPages, @Nullable Album focused,
            List<ListeningDeviceView> devices) implements QueryService.Result {
    }
}
