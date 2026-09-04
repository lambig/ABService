package com.abservice.presentation.rest.album.response;

import org.jspecify.annotations.Nullable;

/**
 * アルバムに対する操作の前提の応答
 *
 * <p>
 * DISCRIMINATED-ENVELOPE: 経路は1本（`operation` で問う操作を示す）で、支障の形は操作ごとに分けている。
 * 両方を満たすため、封筒が問われた操作を持ち、中身は該当する操作の分だけが入る。呼ぶ側は自分が問うた操作の項目を読む。
 * 1つの型へ潰すと、どちらの操作でも使われない項目が混ざる。
 * </p>
 *
 * @param operation
 *            問われた操作（クエリパラメータと同じ綴り）
 * @param deletion
 *            削除の前提（{@code operation} が削除でない場合は null）
 * @param unpublication
 *            非公開化の前提（{@code operation} が非公開化でない場合は null）
 */
public record AlbumPreconditionsResponse(
        String operation,
        @Nullable AlbumDeletionPreconditionsResponse deletion,
        @Nullable AlbumUnpublicationPreconditionsResponse unpublication) {

    /**
     * 削除の前提を持つ応答を組み立てます。
     *
     * @param operation
     *            問われた操作の綴り
     * @param deletion
     *            削除の前提
     * @return 応答
     */
    public static AlbumPreconditionsResponse ofDeletion(
            String operation,
            AlbumDeletionPreconditionsResponse deletion) {
        return new AlbumPreconditionsResponse(
                operation,
                deletion,
                null);
    }

    /**
     * 非公開化の前提を持つ応答を組み立てます。
     *
     * @param operation
     *            問われた操作の綴り
     * @param unpublication
     *            非公開化の前提
     * @return 応答
     */
    public static AlbumPreconditionsResponse ofUnpublication(
            String operation,
            AlbumUnpublicationPreconditionsResponse unpublication) {
        return new AlbumPreconditionsResponse(
                operation,
                null,
                unpublication);
    }
}
