package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * アルバム公開コマンドサービス
 *
 * <p>
 * {@link Album#publish(com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。Albumの公開自体には他集約との整合性制約はありません
 * （非公開Albumを参照する記事を公開できない制約は{@code PublishArticleService}側で検証します）。
 * </p>
 */
@ApplicationScoped
public class PublishAlbumService implements CommandService<PublishAlbumInput, PublishAlbumOutput> {

    private final AlbumRepository albumRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    /**
     * @param albumRepository
     *            アルバムリポジトリ
     * @param businessDateTimeProvider
     *            ビジネス日時プロバイダー
     */
    public PublishAlbumService(
            AlbumRepository albumRepository,
            BusinessDateTimeProvider businessDateTimeProvider) {
        this.albumRepository = albumRepository;
        this.businessDateTimeProvider = businessDateTimeProvider;
    }

    @WithTransaction
    @Override
    public Uni<PublishAlbumOutput> execute(PublishAlbumInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(this::findExisting)
                .flatMap(
                        existing -> businessDateTimeProvider.now()
                                .map(existing::publish))
                .flatMap(albumRepository::save)
                .map(PublishAlbumService::toOutput);
    }

    private Uni<Album> findExisting(Album.Id id) {
        return albumRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Album", id.value()));
    }

    private static PublishAlbumOutput toOutput(Album album) {
        return new PublishAlbumOutput(
                album.id().value(),
                album.title().value(),
                album.isPublished());
    }
}
