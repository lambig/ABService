package com.abservice.application.port;

import io.smallrye.mutiny.Uni;
import java.util.Optional;

/**
 * アセット（画像等のバイナリ）の保管先が満たすべき能力
 *
 * <p>
 * アップロードはクライアントから保管先へ直接行われる（署名付きURL）ため、本ポートはバイト列を受け取らない。
 * アプリケーション層はURLの発行と、アップロード後の実体検査・確定・破棄のみを要求する。
 * </p>
 *
 * <p>
 * 保管場所は「受け入れ前（{@code pending}）」と「配信対象（{@code published}）」に分かれる。クライアントが
 * 書き込めるのは受け入れ前だけで、配信対象へは{@link #publish}による確定でしか実体が入らない。これにより、検査した
 * 実体と配信される実体が確定後にずれない。
 * </p>
 */
public interface AssetStorage {

    /**
     * 受け入れ前の場所へのアップロードを許可する署名付きURLを発行します。
     *
     * @param key
     *            アセットキー
     * @param contentType
     *            アップロードを許可する Content-Type（URLに束縛する）
     * @return 署名付きURLと有効期限
     */
    Uni<PresignedUpload> presignUpload(String key, String contentType);

    /**
     * 受け入れ前のアセットの先頭バイト列とメタ情報を読み出します。
     *
     * @param key
     *            アセットキー
     * @param length
     *            読み出す先頭バイト数
     * @return 実体が存在すればその先頭バイト列とメタ情報、存在しなければ空
     */
    Uni<Optional<StoredAssetHead>> readHead(String key, int length);

    /**
     * 検査に通った受け入れ前の実体を配信対象として確定します。
     *
     * <p>
     * 実体は保管先の内部で複製し、受け入れ前の実体は残さない。配信対象のキーへ書き込める署名付きURLは発行しないため、 確定後に配信される実体は変わらない。
     * </p>
     *
     * @param key
     *            アセットキー
     * @return 完了
     */
    Uni<Void> publish(String key);

    /**
     * 受け入れ前のアセットを破棄します。存在しないキーの破棄は成功として扱います。
     *
     * @param key
     *            アセットキー
     * @return 完了
     */
    Uni<Void> discard(String key);
}
