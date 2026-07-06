package com.abservice.domain.service;

/**
 * ドメインサービスの基底インターフェース
 *
 * <h2>目的</h2>
 * <p>
 * 複数の集約にまたがるビジネスロジックや、単一の集約/値オブジェクトに属さないロジックを実装します。
 * </p>
 *
 * <h2>適用パターン</h2>
 * <ol>
 * <li><strong>複数集約の協調</strong>: 複数の集約を組み合わせたビジネスルール</li>
 * <li><strong>一意性チェック</strong>: メールアドレスの重複確認など</li>
 * <li><strong>複雑な計算</strong>: 特定の集約に属さない計算ロジック</li>
 * <li><strong>外部システム連携</strong>: インフラ層へのインターフェース</li>
 * </ol>
 *
 * <h2>使用例</h2>
 *
 * <pre>
 * {
 *     &#64;code
 *     // 一意性チェック
 *     &#64;ApplicationScoped
 *     public class CatalogNumberUniquenessService implements DomainService {
 *         private final AlbumRepository albumRepository;
 *
 *         public Uni<Boolean> isCatalogNumberUnique(String catalogNumber, AlbumId excludeId) {
 *             return albumRepository.findByCatalogNumber(catalogNumber).onItem()
 *                     .transform(album -> album == null || album.id().equals(excludeId));
 *         }
 *     }
 *
 *     // 複数集約の協調
 *     @ApplicationScoped
 *     public class ArticleAlbumLinkService implements DomainService {
 *         public Uni<Void> linkAlbumToArticle(Article article, Album album) {
 *             article.setAlbumId(album.id()); // AlbumIdを渡す（型安全）
 *             return Uni.createFrom().voidItem();
 *         }
 *     }
 * }
 * </pre>
 *
 * <h2>原則</h2>
 * <ol>
 * <li><strong>ステートレス</strong>: 内部状態を持たない</li>
 * <li><strong>明示的な命名</strong>: ビジネスロジックが分かる名前</li>
 * <li><strong>必要最小限</strong>: 集約で実装できる場合は集約に</li>
 * <li><strong>リアクティブ</strong>: すべてUni&lt;T&gt;で非同期実行</li>
 * </ol>
 */
public interface DomainService {
}
