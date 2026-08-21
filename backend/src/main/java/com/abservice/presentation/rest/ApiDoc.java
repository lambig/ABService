package com.abservice.presentation.rest;

/**
 * OpenAPI 文書のメタ情報（{@link OpenApiDefinition} の注釈値）
 *
 * <p>
 * 注釈値は定数式でなければならず、注釈が付く型自身の定数は非修飾で参照できないため、別クラスに置く。
 * </p>
 */
final class ApiDoc {

    /** API名 */
    static final String TITLE = "ABService API";

    /** API定義のバージョン（パスの {@code /api/v1} と対応する） */
    static final String VERSION = "1";

    /**
     * API全体の説明
     *
     * <p>
     * 認証方式の性質（不透明な固定APIキーをBearerで送る）とエラー応答の共通契約は、エンドポイントごと・スキームごとに 繰り返さずここに一度だけ書く。
     * </p>
     */
    static final String DESCRIPTION = "アルバム・記事の登録と公開を扱うAPI。パスの `/api/v1` はURLバージョニング。"
            + "管理操作（Command系・管理向けQuery・マスタ系Query）は固定APIキーを "
            + "`Authorization: Bearer <APIキー>` で要求する（JWTではない不透明な文字列）。"
            + "エラー応答はすべて RFC 9457 Problem Details（`application/problem+json`）で、"
            + "値検証は400、未存在は404、ビジネスルール違反は409、未認証は401、権限不足は403を返す。";

    /** 認証方式の名前（{@code quarkus.smallrye-openapi.security-scheme-name} と一致させる） */
    static final String ADMIN_KEY = "adminApiKey";

    private ApiDoc() {
    }
}
