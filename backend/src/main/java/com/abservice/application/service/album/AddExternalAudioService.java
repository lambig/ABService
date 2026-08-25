package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.ExternalAudio;
import com.abservice.domain.model.vo.common.ExternalAudioUrl;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 外部音源追加コマンドサービス
 *
 * <p>
 * {@link Album#addExternalAudio(ExternalAudioUrl)}
 * を呼び出すユースケースです。埋め込み可能なホストかどうかは {@link ExternalAudioUrl}
 * が検証し（{@link ValidationException}、400）、同一URLの重複はAlbum集約自身が検証します
 * （{@link com.abservice.domain.exception.BusinessRuleViolationException}、409）。表示順は末尾に採番されます。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AddExternalAudioService implements CommandService<AddExternalAudioInput, AddExternalAudioOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumAccessService albumAccessService;

    @WithTransaction
    @Override
    public Uni<AddExternalAudioOutput> execute(AddExternalAudioInput input) {
        return Uni.createFrom()
                .item(
                        () -> Result.zip(
                                Album.Id.fromInput(input.albumId()),
                                ExternalAudioUrl.fromInput(input.url()),
                                Fields::new)
                                .resolve(ValidationException::new))
                .flatMap(
                        fields -> albumAccessService.findExistingAndClaimEdit(fields.albumId())
                                .map(album -> album.addExternalAudio(fields.url()))
                                .flatMap(
                                        addition -> albumRepository.save(addition.album())
                                                .map(saved -> toOutput(saved, addition.externalAudio()))));
    }

    private record Fields(Album.Id albumId, ExternalAudioUrl url) {
    }

    private static AddExternalAudioOutput toOutput(Album album, ExternalAudio externalAudio) {
        return new AddExternalAudioOutput(
                album.id().value(),
                externalAudio.id().value(),
                externalAudio.displayOrder(),
                externalAudio.url().value().value());
    }
}
