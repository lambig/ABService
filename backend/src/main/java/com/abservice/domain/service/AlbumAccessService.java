package com.abservice.domain.service;

import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;

/**
 * アルバムを掴む唯一の入口となるドメインサービス
 *
 * <p>
 * アルバムの取得は、それ単体では意味を持たない。取得する側は必ず「そのアルバムをこれから書き換える（編集権）」か
 * 「そのアルバムの状態に依拠して別の集約を書き換える（参照）」かのいずれかを主張しており、どちらの主張も、
 * 主張している間に他のトランザクションからアルバムを動かされないことを要求する。本サービスは、その主張を伴う取得だけを
 * 提供する。取得したアルバムは、呼び出し元のトランザクションがコミットするまで他のトランザクションから更新されない
 * （{@link AlbumRepository#findByIdExclusively}）。
 * </p>
 *
 * <p>
 * 主張を伴わない取得（{@link AlbumRepository#findById}）を業務コードから呼ばないことは ArchUnit が検査する。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class AlbumAccessService implements DomainService {

    private final AlbumRepository albumRepository;

    /**
     * これから編集するアルバムを、編集権を主張して取得する
     *
     * @param id
     *            アルバムID
     * @return 存在するAlbum。存在しない場合は{@link EntityNotFoundException}で失敗する
     */
    public Uni<Album> findExistingAndClaimEdit(Album.Id id) {
        return findExisting(id);
    }

    /**
     * 参照先のアルバムを、参照が操作の間ずれないことを主張して取得する
     *
     * <p>
     * アルバム自体は書き換えないが、その状態（公開中か等）に依拠して別の集約を書き換えるため、判定してから書き換えるまでの
     * 間にアルバムが動かないことを要求する。
     * </p>
     *
     * @param id
     *            アルバムID
     * @return 存在するAlbum。存在しない場合は{@link EntityNotFoundException}で失敗する
     */
    public Uni<Album> findExistingAndClaimReference(Album.Id id) {
        return findExisting(id);
    }

    /**
     * 存在すれば編集権を主張して取得する（存在しないことを異常としない）
     *
     * <p>
     * 削除のようにべき等性を保つユースケース向け。対象が無ければ主張するものも無く、空で返る。
     * </p>
     *
     * @param id
     *            アルバムID
     * @return 存在するAlbum。存在しない場合は空
     */
    public Uni<Optional<Album>> findAndClaimEditIfPresent(Album.Id id) {
        return albumRepository.findByIdExclusively(id)
                .map(Optional::ofNullable);
    }

    private Uni<Album> findExisting(Album.Id id) {
        return albumRepository.findByIdExclusively(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Album", id.value()));
    }
}
