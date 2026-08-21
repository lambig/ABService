package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.album.ReorderExternalAudiosOutput.ExternalAudioOrderEntry;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.ExternalAudio;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumExistenceService;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源順序変更コマンドサービス
 *
 * <p>
 * {@link Album#reorderExternalAudios(List)} を呼び出すユースケースです。指定順序の件数がアルバムの保持する
 * 外部音源の件数と一致しない場合はAlbum集約自身が検証します
 * （{@link com.abservice.domain.exception.BusinessRuleViolationException}、409）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ReorderExternalAudiosService
        implements
            CommandService<ReorderExternalAudiosInput, ReorderExternalAudiosOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumExistenceService albumExistenceService;

    @WithTransaction
    @Override
    public Uni<ReorderExternalAudiosOutput> execute(ReorderExternalAudiosInput input) {
        return Uni.createFrom()
                .item(
                        () -> Result.zip(
                                Album.Id.fromInput(input.albumId()),
                                validateOrderedExternalAudioIds(input.orderedExternalAudioIds()),
                                Ids::new)
                                .resolve(ValidationException::new))
                .flatMap(
                        ids -> albumExistenceService.findExisting(ids.albumId())
                                .map(album -> album.reorderExternalAudios(ids.orderedExternalAudioIds()))
                                .flatMap(albumRepository::save)
                                .map(ReorderExternalAudiosService::toOutput));
    }

    private record Ids(Album.Id albumId, List<ExternalAudio.Id> orderedExternalAudioIds) {
    }

    static Result<List<ExternalAudio.Id>> validateOrderedExternalAudioIds(@Nullable List<@Nullable String> values) {
        return Optional.ofNullable(values)
                .map(ReorderExternalAudiosService::sequence)
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "orderedExternalAudioIds",
                                        "Ordered external audio IDs are required",
                                        "EXTERNAL_AUDIO_ORDER_REQUIRED")));
    }

    private static Result<List<ExternalAudio.Id>> sequence(List<@Nullable String> values) {
        return values.stream()
                .map(ExternalAudio.Id::fromInput)
                .reduce(
                        Result.success(List.of()),
                        (acc, next) -> Result.zip(
                                acc,
                                next,
                                ReorderExternalAudiosService::append),
                        (a, b) -> Result.zip(
                                a,
                                b,
                                ReorderExternalAudiosService::concat));
    }

    private static List<ExternalAudio.Id> append(List<ExternalAudio.Id> list, ExternalAudio.Id id) {
        return Stream.concat(list.stream(), Stream.of(id)).toList();
    }

    private static List<ExternalAudio.Id> concat(List<ExternalAudio.Id> a, List<ExternalAudio.Id> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }

    private static ReorderExternalAudiosOutput toOutput(Album album) {
        return new ReorderExternalAudiosOutput(
                album.id().value(),
                album.getExternalAudiosSortedByDisplayOrder().stream()
                        .map(audio -> new ExternalAudioOrderEntry(audio.id().value(), audio.displayOrder()))
                        .toList());
    }
}
