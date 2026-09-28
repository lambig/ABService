package com.abservice.application.query.audio;

import static com.abservice.lib.Iterables.toList;

import com.abservice.application.audio.ListeningDeviceView;
import com.abservice.application.audio.PrivateAudioAccess;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.ListeningDevices;
import com.abservice.application.query.QueryService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;

/** 端末資格情報の一覧。トークンは含まず、状態は照会時刻で表す。機能無効時は未存在として扱う。 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract(Failure.NOT_FOUND)
public class ListListeningDevicesService
        implements
            QueryService<ListListeningDevicesService.Query, ListListeningDevicesService.Result> {
    private final PrivateAudioAccess access;
    private final ListeningDevices devices;

    @Override
    public Uni<Result> query(Query query) {
        return Uni.createFrom().voidItem()
                .invoke(access::requireEnabled)
                .chain(devices::list)
                .map(toList(row -> ListeningDeviceView.of(row, Instant.now())))
                .map(Result::new);
    }

    public record Query() implements QueryService.Query {
    }

    public record Result(List<ListeningDeviceView> devices) implements QueryService.Result {
    }
}
