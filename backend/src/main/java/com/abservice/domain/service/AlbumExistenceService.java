package com.abservice.domain.service;

import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 他集約からAlbumを参照する際の存在確認を扱うドメインサービス
 *
 * <p>
 * Albumを参照する側の集約（Article等）単体では確認できない「Albumの存在」を、{@link AlbumRepository}
 * を介して確認する。公開状態を含む集約をまたぐ不変条件は {@link PublicationConsistencyService} が扱う。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AlbumExistenceService implements DomainService {

    private final AlbumRepository albumRepository;

    /**
     * Albumが存在することを確認して取得する
     *
     * @param id
     *            アルバムID
     * @return 存在するAlbum。存在しない場合は{@link EntityNotFoundException}で失敗する
     */
    public Uni<Album> findExisting(Album.Id id) {
        return albumRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Album", id.value()));
    }
}
