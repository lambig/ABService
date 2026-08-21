package com.abservice.presentation.rest;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * OpenAPI 定義（API全体のメタ情報と認証方式）
 *
 * <p>
 * エンドポイントごとのスキーマ・パス・パラメータは JAX-RS と DTO の型情報から生成されるため注釈を置かない（契約の正は実装）。
 * ここに置くのは型から導けないもの、すなわちAPI全体のメタ情報（{@link ApiDoc}）と認証方式の宣言だけ。管理操作の認証要件は
 * {@code @RolesAllowed}
 * から自動付与される（{@code quarkus.smallrye-openapi.auto-add-security-requirement}）。
 * </p>
 *
 * <p>
 * MicroProfile OpenAPI の仕様上、{@code @OpenAPIDefinition} と
 * {@code @SecurityScheme} は {@link Application}
 * のサブクラスでのみ走査されるため本クラスはそれを継承する。リソースの登録方法は変えない
 * （{@code getClasses}/{@code getSingletons} を上書きしないため、リソースは従来どおり自動検出される）。
 * </p>
 */
@OpenAPIDefinition(info = @Info(title = ApiDoc.TITLE, version = ApiDoc.VERSION, description = ApiDoc.DESCRIPTION))
@SecurityScheme(securitySchemeName = ApiDoc.ADMIN_KEY, type = SecuritySchemeType.HTTP, scheme = "bearer")
public class OpenApiDefinition extends Application {
}
