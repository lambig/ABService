package com.abservice.application.query.audio;

import com.abservice.application.audio.AudioApiValues;
import com.abservice.application.audio.ListeningDeviceView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.query.QueryService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.infrastructure.persistence.datasource.ListeningPreparationDataSource;
import com.abservice.infrastructure.persistence.datasource.ListeningPreparationRow;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/** 一覧・配布可否・端末を一要求で照会する。準備状態は端末への保存完了を表さない。 */
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
        return Uni.createFrom().item(() -> validate(query))
                .chain(id -> preparations.page(query.page(), id))
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

    private @Nullable String validate(Query query) {
        access.requireEnabled();
        Optional.of(query.page())
                .filter(page -> page >= 0)
                .filter(page -> page <= Integer.MAX_VALUE / ListeningPreparationDataSource.PAGE_SIZE)
                .orElseThrow(
                        () -> new ValidationException(
                                List.of(
                                        new ErrorResult(
                                                "page",
                                                "有効なページを指定してください",
                                                "INVALID_PAGE"))));
        return Optional.ofNullable(query.albumId())
                .map(id -> AudioApiValues.uuid(id, "albumId").toString())
                .orElse(null);
    }

    private static void requireFocus(Query query, ListeningPreparationDataSource.Page page) {
        Optional.ofNullable(query.albumId()).ifPresent(
                id -> Optional.ofNullable(page.focused())
                        .orElseThrow(() -> EntityNotFoundException.of("Album", id)));
    }

    public record Query(int page, @Nullable String albumId) implements QueryService.Query {
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
