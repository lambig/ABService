package com.abservice.domain.service;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 他集約からAlbumを参照する際の存在確認・公開状態確認を扱うドメインサービス
 *
 * <p>
 * Albumを参照する側の集約（Article等）単体では検証できない「Albumの存在・公開状態」を、 {@link AlbumRepository}
 * を介して確認する。
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

    /**
     * Albumが存在し、かつ公開中であることを確認して取得する
     *
     * @param id
     *            アルバムID
     * @return 公開中のAlbum。存在しない場合は{@link EntityNotFoundException}、
     *         非公開の場合は{@link BusinessRuleViolationException}で失敗する
     */
    public Uni<Album> findPublic(Album.Id id) {
        return findExisting(id)
                .flatMap(AlbumExistenceService::requirePublished);
    }

    private static Uni<Album> requirePublished(Album album) {
        return album.isPublished()
                ? Uni.createFrom().item(album)
                : Uni.createFrom()
                        .failure(new BusinessRuleViolationException("アルバムが非公開です"));
    }
}
