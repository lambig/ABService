package com.abservice.presentation.rest.security;

/**
 * 認可で用いるロール名
 *
 * <p>
 * 個人利用前提のため、ロールは管理者と試聴端末の2種。公開向けの参照は認証不要（ロール要求なし）とし、
 * 管理操作（Command系・下書きを含む管理向けQuery）に管理者ロールを、配布パッケージの取得に端末ロールを要求する。
 * 端末は管理者ではなく、管理者は端末ではない——配布の入口は準備する端末だけに開く。
 * </p>
 */
public final class SecurityRoles {

    /** 管理者ロール。Command系および管理向けQueryのエンドポイントが要求する */
    public static final String ADMIN = "admin";

    /** 試聴端末ロール。管理者が発行した期限付きの端末トークンで認証した要求だけが持つ */
    public static final String LISTENER = "listener";

    private SecurityRoles() {
    }
}
