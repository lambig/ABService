package com.abservice.domain.repository;

import com.abservice.domain.model.aggregate.Aggregate;
import io.smallrye.mutiny.Uni;

/**
 * リポジトリの基底インターフェース
 *
 * <h2>基本原則</h2>
 * <ul>
 * <li><strong>集約ルートにのみ提供</strong>: 集約内エンティティにはRepositoryを作らない</li>
 * <li><strong>コレクション指向</strong>: ドメインオブジェクトのコレクションとして振る舞う</li>
 * <li><strong>集約全体を永続化</strong>: 集約内のエンティティと値オブジェクトも一緒に保存</li>
 * <li><strong>リアクティブ</strong>: すべての操作はMutiny Uni&lt;T&gt;で非同期実行</li>
 * </ul>
 *
 * <h2>配置</h2>
 * <ul>
 * <li>インターフェース: {@code domain.repository} (ドメイン層)</li>
 * <li>実装: {@code infrastructure.persistence} (インフラ層)</li>
 * </ul>
 *
 * <h2>使用例</h2>
 *
 * <pre>{@code
 * interface AlbumRepository extends Repository<Album, Album.Id> {
 *     Uni<Album> findByTitle(AlbumTitle title);
 * }
 *
 * // CommandServiceでの使用
 * albumRepository.findById(id)
 *     .onItem().ifNull().failWith(() -> new AlbumNotFoundException(id))
 *     .flatMap(album -> albumRepository.save(album))
 * }</pre>
 *
 * @param <T>
 *            集約ルートの型
 * @param <ID>
 *            集約ルートのIDの型
 */
public interface Repository<T extends Aggregate<T, ID>, ID> {

    /**
     * 集約を永続化（新規追加または更新）
     *
     * @param aggregate
     *            永続化する集約ルート
     * @return 永続化された集約
     */
    Uni<T> save(T aggregate);

    /**
     * 複数の集約を一括永続化
     *
     * @param aggregates
     *            永続化する集約のIterable
     * @return 永続化された集約のリスト
     */
    Uni<java.util.List<T>> saveAll(Iterable<T> aggregates);

    /**
     * IDで集約を取得
     *
     * @param id
     *            集約のID
     * @return 集約、存在しない場合はnull
     */
    Uni<T> findById(ID id);

    /**
     * 複数のIDで集約を取得
     *
     * @param ids
     *            集約IDのIterable
     * @return 取得した集約のリスト
     */
    Uni<java.util.List<T>> findAllById(Iterable<ID> ids);

    /**
     * すべての集約を取得
     *
     * <p>
     * <strong>注意</strong>: 本番環境ではページネーション推奨
     * </p>
     *
     * @return すべての集約のリスト
     */
    Uni<java.util.List<T>> findAll();

    /**
     * 集約を削除
     *
     * @param aggregate
     *            削除する集約
     * @return 完了を示すUni&lt;Void&gt;
     */
    Uni<Void> delete(T aggregate);

    /**
     * 複数の集約を一括削除
     *
     * @param aggregates
     *            削除する集約のIterable
     * @return 完了を示すUni&lt;Void&gt;
     */
    Uni<Void> deleteAll(Iterable<T> aggregates);

    /**
     * IDで集約を削除
     *
     * @param id
     *            削除する集約のID
     * @return 完了を示すUni&lt;Void&gt;
     */
    Uni<Void> deleteById(ID id);

    /**
     * 複数のIDで集約を削除
     *
     * @param ids
     *            削除する集約IDのIterable
     * @return 完了を示すUni&lt;Void&gt;
     */
    Uni<Void> deleteAllById(Iterable<ID> ids);

    /**
     * 集約の存在確認
     *
     * @param id
     *            確認する集約のID
     * @return 存在する場合true
     */
    Uni<Boolean> existsById(ID id);

    /**
     * 集約の総数を取得
     *
     * @return 集約の総数
     */
    Uni<Long> count();
}
