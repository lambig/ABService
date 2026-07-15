package com.abservice.domain.model.entity;

import com.abservice.domain.model.DomainObject;
import java.util.Optional;

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
 *     &#64;code
 *     // ✅ 推奨実装（Lombok @With使用、witherはprivate）
 *     &#64;With(AccessLevel.PRIVATE) // witherメソッドをprivateに
 *     &#64;Getter
 *     &#64;AllArgsConstructor
 *     &#64;EqualsAndHashCode(onlyExplicitlyIncluded = true)
 *     public class Album implements Aggregate<Album, Album.Id> {
 *         @EqualsAndHashCode.Include
 *         private final Album.Id id;
 *         private final AlbumTitle title;
 *         private final CatalogNumber catalogNumber;
 *
 *         // Lombokが生成: private withTitle(), private withCatalogNumber()
 *
 *         // 業務的な意味を持つpublicメソッドを提供（検証はVOと式で表現し、if文は使わない）
 *         public Album changeTitle(AlbumTitle newTitle) {
 *             return withTitle( // private witherを使用
 *                     Optional.ofNullable(newTitle)
 *                             .orElseThrow(() -> new IllegalArgumentException("Title cannot be null")));
 *         }
 *
 *         public Album changeCatalogNumber(CatalogNumber newCatalogNumber) {
 *             return withCatalogNumber( // private witherを使用
 *                     Optional.ofNullable(newCatalogNumber)
 *                             .orElseThrow(() -> new IllegalArgumentException("Catalog number cannot be null")));
 *         }
 *     }
 *
 *     // ✅ EntityIdの実装（record・コンパクトコンストラクタで検証）
 *     public record Id(String value) implements EntityId<Album> {
 *         public Id {
 *             Policy.<String>all(
 *                     Policy.of(
 *                             StringUtils::isNotBlank,
 *                             () -> new ErrorResult("value", "Album ID cannot be blank", "ID_BLANK")),
 *                     Policy.of(
 *                             EntityId::isValidUuid,
 *                             () -> new ErrorResult("value", "Album ID must be a valid UUID", "ID_INVALID_UUID")))
 *                     .verify(value, Function.identity())
 *                     .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
 *         }
 *     }
 *
 *     // ❌ 禁止
 *     public class Album {
 *         private String title;
 *         public void setTitle(String title) {
 *             this.title = title;
 *         } // Setter禁止
 *     }
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
        return Optional.ofNullable(other)
                .filter(o -> this.getClass().equals(o.getClass()))
                .filter(o -> this.id().equals(o.id()))
                .isPresent();
    }

    /**
     * 指定した識別子を持つエンティティかどうか（同一性比較）。
     *
     * <p>
     * id を外部で取り出して比較させず、識別子との同一性判定をエンティティ自身の責務とする。
     * </p>
     *
     * @param id
     *            比較対象の識別子
     * @return 同一の識別子なら true
     */
    default boolean hasId(ID id) {
        return this.id().equals(id);
    }
}
