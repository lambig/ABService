package com.abservice.application.query;

/**
 * Query の要求元（誰向けの照会か）
 *
 * <p>
 * 同じ照会ユースケースを公開向け・管理向けの2系統に増殖させる代わりに、Query 側の DTO が本 enum
 * を保持することで対象範囲を切り替える。認証・認可はエンドポイント側（{@code @RolesAllowed}）が担い、 本 enum
 * はその結果として「どこまで見せるか」を表す。
 * </p>
 */
public enum Audience {

    /** 認証不要の公開向け。公開中のもののみを対象にする */
    PUBLIC,

    /** 管理者向け。下書きを含む全件を対象にする */
    ADMIN
}
