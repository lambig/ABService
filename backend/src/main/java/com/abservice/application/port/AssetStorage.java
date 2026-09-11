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
     * 実体は保管先の内部で複製する。配信対象のキーへ書き込める署名付きURLは発行しないため、確定後に配信される実体は
     * 変わらない。受け入れ前の実体は確定後に片付けるが、片付けの失敗は確定を取り消さない。
     * </p>
     *
     * <p>
     * 確定が成立するのは、<strong>コピー元が検査したその実体</strong> であり、かつ <strong>そのキーがまだ確定して
     * いない</strong> 場合だけ。この2つは1回の操作の条件として同時に判定されるため、同じキーの確定が同時に走っても
     * 配信対象へ入るのは先に成立した1つだけになる。条件を満たせなければ {@link AssetConfirmConflictException}
     * で失敗する（#285）。
     * </p>
     *
     * @param key
     *            アセットキー
     * @param entityTag
     *            検査した実体の識別子（{@link StoredAssetHead#entityTag()}）
     * @return 完了
     */
    Uni<Void> publish(String key, String entityTag);

    /**
     * 配信対象として既に確定済みかを返します。
     *
     * <p>
     * 確定済みのキーを確定し直そうとする要求を早い段階で断り、無駄な検査を省くための問い合わせ。この答えと確定の間に
     * 別の確定が挟まり得るため、これ自体は同時に走る確定を防がない。配信される実体を1つに保つのは {@link #publish}
     * の条件であり、この問い合わせを省いても正しさは変わらない。
     * </p>
     *
     * @param key
     *            アセットキー
     * @return 確定済みなら {@code true}
     */
    Uni<Boolean> isPublished(String key);

    /**
     * 受け入れ前のアセットを破棄します。存在しないキーの破棄は成功として扱います。
     *
     * @param key
     *            アセットキー
     * @return 完了
     */
    Uni<Void> discard(String key);
}
