package com.abservice.domain.service;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * ArticleがAlbumへ持つ参照（{@code albumId}）に関する、集約をまたぐ整合性ルールを扱うドメインサービス
 *
 * <p>
 * Article集約単体では検証できない「参照先Albumの公開状態」を、{@link AlbumRepository} を介して確認する。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ArticleAlbumReferencePolicy implements DomainService {

    private final AlbumRepository albumRepository;

    /**
     * 参照先Albumが存在し、かつ公開中であることを確認する
     *
     * @param albumId
     *            参照先アルバムID
     * @return 公開中のAlbum。存在しない場合は{@link EntityNotFoundException}、
     *         非公開の場合は{@link BusinessRuleViolationException}で失敗する
     */
    public Uni<Album> requirePublishedAlbum(Album.Id albumId) {
        return albumRepository.findById(albumId)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Album", albumId.value()))
                .flatMap(ArticleAlbumReferencePolicy::requirePublished);
    }

    private static Uni<Album> requirePublished(Album album) {
        return album.isPublished()
                ? Uni.createFrom().item(album)
                : Uni.createFrom()
                        .failure(
                                new BusinessRuleViolationException(
                                        "参照先のアルバムが非公開のため記事を公開できません"));
    }
}
