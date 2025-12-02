package com.abservice.domain.model.aggregate;

import com.abservice.domain.model.entity.DomainEntity;

/**
 * 集約ルート（Aggregate Root）のインターフェース
 *
 * <p>ドメイン駆動設計における集約（Aggregate）の境界を定義します。</p>
 *
 * <h2>集約ルートの責務</h2>
 * <ol>
 *   <li><strong>整合性境界の定義</strong>: 一貫性を保つべきドメインオブジェクトの境界</li>
 *   <li><strong>トランザクション境界</strong>: 単一トランザクション内で永続化</li>
 *   <li><strong>永続化の単位</strong>: Repositoryは集約ルートにのみ提供</li>
 *   <li><strong>不変条件の保護</strong>: 集約全体の整合性を維持</li>
 * </ol>
 *
 * <h2>集約の設計原則</h2>
 *
 * <h3>集約ルートにすべきもの</h3>
 * <ul>
 *   <li>独立したライフサイクルを持つ</li>
 *   <li>独自のビジネスルール・整合性制約を持つ</li>
 *   <li>IDで他の集約から参照される</li>
 *   <li>直接検索・取得される必要がある</li>
 * </ul>
 *
 * <h3>集約は小さく保つ</h3>
 * <ul>
 *   <li>単一エンティティのみの集約も有効</li>
 *   <li>大きすぎる集約はパフォーマンス問題を引き起こす</li>
 *   <li>集約境界はビジネスルールとトランザクション要件で判断</li>
 * </ul>
 *
 * <h2>集約間の参照</h2>
 * <p>集約間はIDで参照します（オブジェクト参照は禁止）：</p>
 *
 * <pre>{@code
 * // ✅ 正しい: 型安全なID参照
 * public record Album(AlbumId id, DriverLicenseId driverLicenseId)
 *     implements Aggregate<Album, AlbumId> {
 * }
 *
 * // ❌ 間違い: オブジェクト参照
 * public record Album(AlbumId id, DriverLicense driverLicense)  // 集約境界違反
 *     implements Aggregate<Album, AlbumId> {
 * }
 * }</pre>
 *
 * <h2>Repositoryとの関係</h2>
 * <pre>{@code
 * // ✅ 集約ルートにRepositoryを提供
 * interface AlbumRepository extends Repository<Album, AlbumId>
 *
 * // ❌ 集約内エンティティにRepositoryは作らない
 * // interface PersonalDetailRepository  // 作成禁止
 * }</pre>
 *
 * @param <T> 実装クラスの型（CRTP）
 * @param <ID> エンティティの識別子の型
 */
public interface Aggregate<T extends Aggregate<T, ID>, ID> extends DomainEntity<T, ID> {
}
