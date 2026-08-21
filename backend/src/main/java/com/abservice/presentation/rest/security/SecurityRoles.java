package com.abservice.presentation.rest.security;

/**
 * 認可で用いるロール名
 *
 * <p>
 * 個人利用前提のため、ロールは管理者の1種のみ。公開向けの参照は認証不要（ロール要求なし）とし、
 * 管理操作（Command系・下書きを含む管理向けQuery）に本ロールを要求する。
 * </p>
 */
public final class SecurityRoles {

    /** 管理者ロール。Command系および管理向けQueryのエンドポイントが要求する */
    public static final String ADMIN = "admin";

    private SecurityRoles() {
    }
}
