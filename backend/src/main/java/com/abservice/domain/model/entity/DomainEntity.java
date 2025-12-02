package com.abservice.domain.model.entity;

import com.abservice.domain.model.DomainObject;

/**
 * ドメインエンティティのインターフェース
 *
 * <p>
 * ドメイン駆動設計におけるエンティティ（Entity）を表します。
 * </p>
 *
 * <h2>特性</h2>
 * <ul>
 * <li><strong>同一性（Identity）</strong>: 一意な識別子（ID）によって区別される</li>
 * <li><strong>可変性（Mutability）</strong>: ライフサイクルを通じて状態が変化しうる</li>
 * <li><strong>連続性（Continuity）</strong>: 状態が変わっても同一のエンティティとして追跡される</li>
 * </ul>
 *
 * <h2>状態変更ルール：Lombok @Withパターン推奨</h2>
 * <p>
 * エンティティの状態変更は<strong>Lombok {@code @With}アノテーション</strong>を使用して 不変更新（immutable
 * update）を実現します。Setterの使用は禁止です。
 * </p>
 *
 * <p>
 * <strong>重要</strong>: {@code @With}で生成されるwitherメソッドは{@code private}にし、
 * 業務的な意味を持つpublicメソッド内で使用してください。
 * </p>
 *
 * <pre>
 * {
 * 	&#64;code
 * 	// ✅ 推奨実装（Lombok @With使用、witherはprivate）
 * 	&#64;With(AccessLevel.PRIVATE) // witherメソッドをprivateに
 * 	&#64;Getter
 * 	&#64;AllArgsConstructor
 * 	&#64;EqualsAndHashCode(onlyExplicitlyIncluded = true)
 * 	public class Album implements Aggregate<Album, AlbumId> {
 * 		@EqualsAndHashCode.Include
 * 		private final AlbumId id;
 * 		private final String name;
 * 		private final String catalogNumber;
 *
 * 		// Lombokが生成: private withName(), private withCatalogNumber()
 *
 * 		// 業務的な意味を持つpublicメソッドを提供
 * 		public Album changeName(String newName) {
 * 			// バリデーション
 * 			if (newName == null || newName.isBlank()) {
 * 				throw new IllegalArgumentException("Name cannot be empty");
 * 			}
 * 			return withName(newName); // private witherを使用
 * 		}
 *
 * 		public Album updateCatalogNumber(String newCatalogNumber) {
 * 			// バリデーション
 * 			if (newCatalogNumber == null || !newCatalogNumber.contains("@")) {
 * 				throw new IllegalArgumentException("Invalid catalogNumber");
 * 			}
 * 			return withCatalogNumber(newCatalogNumber); // private witherを使用
 * 		}
 * 	}
 *
 * 	// ✅ Value Objectの実装（Java Records推奨）
 * 	public record AlbumId(UUID value) implements EntityId<AlbumId> {
 * 		// Recordは自動的に不変
 * 	}
 *
 * 	// ❌ 禁止
 * 	public class Album {
 * 		private String catalogNumber;
 * 		public void setCatalogNumber(String catalogNumber) {
 * 			this.catalogNumber = catalogNumber;
 * 		} // Setter禁止
 * 	}
 * }
 * </pre>
 *
 * <h2>等価性</h2>
 * <p>
 * エンティティの等価性はIDのみで判定されます。 他の属性が異なってもIDが同じなら同一エンティティです。
 * </p>
 *
 * @param <T>
 *            実装クラスの型（CRTP）
 * @param <ID>
 *            エンティティの識別子の型
 */
public interface DomainEntity<T extends DomainEntity<T, ID>, ID> extends DomainObject<T> {
	/**
	 * エンティティの識別子を取得する
	 *
	 * @return エンティティの一意な識別子
	 */
	ID id();

	/**
	 * 等価性を検証する（デフォルト実装）
	 *
	 * <p>
	 * エンティティの等価性は識別子（ID）によってのみ判定されます。 両方のエンティティのIDが等しい場合、等価とみなされます。
	 * </p>
	 *
	 * @param other
	 *            比較対象のオブジェクト
	 * @return 等価である場合はtrue、そうでない場合はfalse
	 */
	@Override
	default boolean equivalentTo(T other) {
		if (other == null) {
			return false;
		}
		if (this == other) {
			return true;
		}
		if (!this.getClass().equals(other.getClass())) {
			return false;
		}
		return this.id().equals(other.id());
	}
}
