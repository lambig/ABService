package com.abservice.application.service.album;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumCreationService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * アルバム作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateAlbumInput}）から新規 {@link Album} を生成して永続化するユースケースです。
 * 値検証・組み立ては{@link AlbumCreationService}（トラックを同時登録する
 * {@code RegisterAlbumWithTracksService}とも共有するドメインサービス）に委譲します。
 * </p>
 *
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract(Failure.VALIDATION)
public class CreateAlbumService implements CommandService<CreateAlbumInput, CreateAlbumOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumCreationService albumCreationService;

    @WithTransaction
    @Override
    public Uni<CreateAlbumOutput> execute(CreateAlbumInput input) {
        return input.asValidated(CreateAlbumInput::parsed)
                .map(albumCreationService::create)
                .flatMap(albumRepository::save)
                .map(CreateAlbumService::toOutput);
    }

    private static CreateAlbumOutput toOutput(Album album) {
        return new CreateAlbumOutput(
                album.id()
                        .value(),
                album.title()
                        .value(),
                album.releaseDate()
                        .asLocalDate()
                        .toString(),
                album.artistCredit()
                        .displayName()
                        .value());
    }
}
