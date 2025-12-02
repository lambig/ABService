package com.abservice.domain.factory;

import com.abservice.domain.model.aggregate.Aggregate;
import io.smallrye.mutiny.Uni;

/**
 * ファクトリの基底インターフェース
 *
 * <h2>目的</h2>
 * <p>複雑な集約の生成ロジックをカプセル化します。</p>
 *
 * <h2>適用パターン</h2>
 * <ol>
 *   <li><strong>複雑な初期化</strong>: 複数のエンティティと値オブジェクトを組み合わせた集約生成</li>
 *   <li><strong>外部依存の生成</strong>: リポジトリや他の集約を参照した生成</li>
 *   <li><strong>不変条件の保証</strong>: 生成時に複雑なバリデーションが必要</li>
 *   <li><strong>再構成</strong>: 永続化されたデータから集約を再構成</li>
 * </ol>
 *
 * <h2>使用例</h2>
 * <pre>{@code
 * @ApplicationScoped
 * public class AlbumFactory implements Factory<Album, AlbumFactory.CreateParams> {
 *
 *     private final CatalogNumberUniquenessService catalogNumberUniquenessService;
 *
 *     public record CreateParams(
 *         String name,
 *         String catalogNumber,
 *         String phoneNumber
 *     ) implements Factory.Params {}
 *
 *     @Override
 *     public Uni<Album> create(CreateParams params) {
 *         AlbumTitle catalogNumber = AlbumTitle.of(params.catalogNumber());
 *
 *         return catalogNumberUniquenessService.isCatalogNumberUnique(catalogNumber, null)
 *             .onItem().transform(isUnique -> {
 *                 if (!isUnique) {
 *                     throw new CatalogNumberAlreadyExistsException(catalogNumber);
 *                 }
 *
 *                 return Album.create(
 *                     name: params.name(),
 *                     catalogNumber: catalogNumber,
 *                     phoneNumber: PhoneNumber.of(params.phoneNumber())
 *                 );
 *             });
 *     }
 * }
 * }</pre>
 *
 * <h2>原則</h2>
 * <ol>
 *   <li><strong>生成に集中</strong>: 集約の生成ロジックのみ</li>
 *   <li><strong>不変条件の保証</strong>: 生成時に必ずバリデーション</li>
 *   <li><strong>依存の注入</strong>: DomainServiceやRepositoryをコンストラクタ注入</li>
 *   <li><strong>リアクティブ</strong>: すべてUni&lt;T&gt;で非同期実行</li>
 * </ol>
 *
 * @param <T> 生成する集約の型
 * @param <P> 生成パラメータの型
 */
public interface Factory<T extends Aggregate<T, ?>, P extends Factory.Params> {
    /**
     * 集約を生成する
     *
     * @param params 生成パラメータ
     * @return 生成された集約を含むUni
     */
    Uni<T> create(P params);

    /**
     * ファクトリのパラメータマーカーインターフェース
     */
    interface Params {
    }
}
