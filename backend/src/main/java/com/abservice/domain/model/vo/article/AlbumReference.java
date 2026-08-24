package com.abservice.domain.model.vo.article;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 記事からアルバムへの参照
 *
 * <p>
 * 参照の状態を型で表す sealed 型です。参照なし（{@link None}）・有効な参照（{@link Referenced}）・失効した参照
 * （{@link Lost}）の3状態を取ります。参照先のアルバムが削除された場合は {@link Lost} へ遷移し、旧アルバムID・失効日時・
 * 理由が残ります。
 * </p>
 */
public sealed interface AlbumReference extends ValueObject<AlbumReference> {

    /**
     * 参照を持たない状態
     */
    record None() implements AlbumReference {

        @Override
        public Optional<Album.Id> activeAlbumId() {
            return Optional.empty();
        }

        @Override
        public Optional<Lost> lost() {
            return Optional.empty();
        }

        @Override
        public boolean equivalentTo(AlbumReference other) {
            return switch (other) {
                case None ignored -> true;
                case Referenced ignored -> false;
                case Lost ignored -> false;
            };
        }
    }

    /**
     * 有効なアルバム参照を持つ状態
     *
     * @param albumId
     *            参照先アルバムのID
     */
    record Referenced(Album.@NonNull Id albumId) implements AlbumReference {

        @Override
        public Optional<Album.Id> activeAlbumId() {
            return Optional.of(albumId);
        }

        @Override
        public Optional<Lost> lost() {
            return Optional.empty();
        }

        @Override
        public boolean equivalentTo(AlbumReference other) {
            return other.activeAlbumId()
                    .filter(albumId::equals)
                    .isPresent();
        }
    }

    /**
     * 参照先が失われた状態
     *
     * @param formerAlbumId
     *            失効した参照先アルバムのID
     * @param lostAt
     *            参照が失効した日時
     * @param reason
     *            失効した理由
     */
    record Lost(
            Album.@NonNull Id formerAlbumId,
            @NonNull BusinessDateTime lostAt,
            @NonNull AlbumReferenceLostReason reason) implements AlbumReference {

        @Override
        public Optional<Album.Id> activeAlbumId() {
            return Optional.empty();
        }

        @Override
        public Optional<Lost> lost() {
            return Optional.of(this);
        }

        @Override
        public boolean equivalentTo(AlbumReference other) {
            return other.lost()
                    .filter(o -> formerAlbumId.equals(o.formerAlbumId()))
                    .filter(o -> lostAt.equals(o.lostAt()))
                    .filter(o -> reason == o.reason())
                    .isPresent();
        }
    }

    /**
     * 参照を持たない状態を返します。
     *
     * @return 参照なし
     */
    static AlbumReference none() {
        return new None();
    }

    /**
     * 有効な参照を生成します。参照先が未指定なら参照なしを返します。
     *
     * @param albumId
     *            参照先アルバムのID（nullable）
     * @return 参照先があれば有効な参照、なければ参照なし
     */
    static AlbumReference of(Album.@Nullable Id albumId) {
        return Optional.ofNullable(albumId)
                .<AlbumReference>map(Referenced::new)
                .orElseGet(AlbumReference::none);
    }

    /**
     * 現在有効な参照先のIDを返します。
     *
     * @return 有効な参照を持つ場合はその参照先ID、それ以外は空
     */
    Optional<Album.Id> activeAlbumId();

    /**
     * 失効した参照の情報を返します。
     *
     * @return 失効している場合はその情報、それ以外は空
     */
    Optional<Lost> lost();
}
