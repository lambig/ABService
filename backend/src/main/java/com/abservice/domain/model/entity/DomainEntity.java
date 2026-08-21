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
 * <p>
 * 参照実装: {@code domain.model.aggregate.album.Album}（wither は private、業務的な意味を持つ
 * public メソッドを提供）。
 * </p>
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
