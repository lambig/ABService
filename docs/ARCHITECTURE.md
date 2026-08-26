# ABService アーキテクチャ

> **このドキュメントの位置づけ**
> コード単体からは見えない全体像（構成・境界・経路の決定）を記述する。依存ライブラリのバージョンは `backend/build.gradle`、型階層・クラス構成は実クラス、テーブル定義はマイグレーションが正であり、ここでは再記述しない。なぜその構造にしたかは [DECISIONS.md](DECISIONS.md)、どう書くかは [CODING_GUIDELINES.md](CODING_GUIDELINES.md)。

## 構成

モノリポジトリ。バックエンドは Quarkus（Reactive）、フロントエンドは管理画面・公開画面ともに Astro + Svelte で静的ビルドする。

```
                         ┌──────────────────────────┐
                         │       CloudFront         │  単一ドメイン・パスベース
                         └────────────┬─────────────┘
        ┌─────────────────┬───────────┼───────────┬─────────────────┐
        │ /               │ /admin/*  │ /api/*    │ /assets/*       │
┌───────▼────────┐ ┌──────▼───────┐ ┌─▼──────────┐ ┌──────▼────────┐
│ frontend-public│ │frontend-admin│ │  backend   │ │ assets (S3)   │
│  (S3, Astro)   │ │ (S3, Astro)  │ │(EC2/Quarkus│ │  OAC読み取り   │
└────────────────┘ └──────────────┘ └─┬──────────┘ └───────────────┘
                                      │
                              ┌───────▼────────┐
                              │ RDS PostgreSQL │
                              └────────────────┘
```

ローカル開発では PostgreSQL と MinIO（S3互換）を docker compose で起動し、フロントエンドとバックエンドはそれぞれの開発サーバで動かす（[../docker/README.md](../docker/README.md)）。AWS 側の構成とデプロイ経路は [../infra/README.md](../infra/README.md) が正。

## レイヤと依存方向

`domain` → 依存なし / `application` → domain / `infrastructure` → application・domain / `presentation` → application・domain。CQRS の非対称として、Command は Repository（Write Model）経由でドメインを通し、Query は DataSource から Read Model DTO を直接読む。この依存方向と返却型契約は ArchUnit が強制する（[CODING_GUIDELINES.md](CODING_GUIDELINES.md) §1）。

## 認証・認可アーキテクチャ

### 認証方式
- **方式**: 固定APIキー。クライアントは `Authorization: Bearer <APIキー>` を付与する（個人利用が前提のため OIDC/Keycloak は採用しない。将来複数ユーザー・ロール管理が必要になった時点で再検討する）
- **実装**: `presentation/rest/security` の `ApiKeyAuthenticationMechanism`（Quarkus Security の `HttpAuthenticationMechanism`）がヘッダからキーを抽出し、`ApiKeyIdentityProvider` が設定値 `abservice.auth.admin-api-key` と定数時間比較（`MessageDigest.isEqual`）して管理者ロールの `SecurityIdentity` を発行する
- **キーの供給**: 環境変数 `ADMIN_API_KEY`。本番は Parameter Store（SecureString）の値をデプロイスクリプトが注入し、prod プロファイルでは未設定なら起動に失敗する
- **セッション**: ステートレス（サーバー側セッションを持たない）
- **スキーム選択の理由**: Bearer に揃えることで、将来 OIDC のアクセストークンへ差し替えてもクライアント契約が変わらない

### 認可方式
- **ロール**: 管理者（`admin`）の1種のみ。`SecurityRoles.ADMIN` を唯一の定義とする
- **アノテーション**: リソースクラスへ `@RolesAllowed(SecurityRoles.ADMIN)` を付与する
- **認証必須**: 全集約の `*CommandResource`（作成・更新・削除・公開/非公開）、管理向けQuery（`/api/v1/admin/**`）、公開サイトが参照しないマスタ系Query（`/api/v1/tunes`）
- **認証不要**: 公開向けQuery（`/api/v1/albums`・`/api/v1/articles`）。`Audience.PUBLIC` として公開中のものだけを返し、下書きは未存在として扱う
- **強制**: ArchUnit で `*CommandResource` / `*AdminQueryResource` への `@RolesAllowed` 付与を必須にする

### セキュリティフロー
1. クライアント（管理画面・運用者）が `Authorization: Bearer <APIキー>` を付けてバックエンドAPIを呼ぶ
2. 認証メカニズムがキーを抽出し、IdentityProvider が設定値と照合して管理者ロールを付与する
3. `@RolesAllowed` が付いたエンドポイントはロールを検査し、公開向けQueryは匿名のまま処理する
4. 未認証・キー不正は 401（`WWW-Authenticate: Bearer realm="abservice"` 付き）、権限不足は 403 を、いずれも RFC 9457 Problem Details（`urn:abservice:error:UNAUTHORIZED` / `FORBIDDEN`）で返す

## API境界の方針

- **バージョニング**: URLパス（`/api/v1/...`）
- **CQRS の分割**: 集約ごとに Command リソースと Query リソースを分ける。公開向けQueryは公開中のものだけを返し、下書きを含む照会は `/api/v1/admin/**` に分ける
- **エラー応答**: 全て RFC 9457 Problem Details（`application/problem+json`）。値検証400 / 未存在404 / ビジネス違反409 の対応づけは [CODING_GUIDELINES.md](CODING_GUIDELINES.md) §6。専用マッパーを持たない例外も同形式で返し、想定外の500は内部情報を載せずログと突き合わせる識別子だけを返す
- **一覧の並び順**: `sort` / `direction` のクエリパラメータで選ぶ。許可値は集約ごとの列挙で閉じ、要求元（公開向け／管理向け）で集合が異なる。未指定時は登録の新しい順で、同値時のタイブレークは常に `domainId`。許可外の値は既定へ落とさず 400。判断の理由は [DECISIONS.md](DECISIONS.md) 11
- **削除に伴う参照の失効**: Album の削除は参照していた記事の参照を失効させ、公開中だった記事を非公開へ戻す。影響を受けた記事は応答（200）に含めてクライアントへ通知する。失効した参照を持つアルバム記事は公開できない。判断の理由は [DECISIONS.md](DECISIONS.md) 12
- **参照されている対象の削除**: Tune の削除は、トラック内のチューン構成から参照されている間は 409 で拒否する（参照を外す操作を先に要求し、カスケードで参照を外さない）。判断の理由は [DECISIONS.md](DECISIONS.md) 17
- **ドメインオブジェクトを公開しない**: Request/Response DTO を境界に置く
- **CORS**: 本番はフロントエンドとAPIが同一オリジン（単一ドメインのパスベースルーティング）になるため無効。開発・テストのみ有効にし、フロントエンドの開発サーバのオリジンを許可する。別オリジン構成へ変える場合は有効化フラグと許可オリジンを対で指定する
- **観測性**: ヘルスチェック（`/q/health/{live,ready}`。readiness はDB接続確認を含む）とメトリクス（`/q/metrics`、Prometheus形式）は公開APIのパスに混ぜず `/q/*` に置く。判断の理由は [DECISIONS.md](DECISIONS.md) 10
- **API定義**: OpenAPI 文書は JAX-RS と DTO の型情報から生成し、エンドポイントごとの注釈は置かない（契約の正は実装）。型から導けないAPI全体のメタ情報と認証方式のみ宣言し、管理操作の認証要件は `@RolesAllowed` から自動付与する。Swagger UI は開発時のみで、CloudFront は `/api/*` だけを backend へ流すため `/q/*` は本番で外部に露出しない

## アセット（画像）のアップロードと配信

実体は backend を経由せず、クライアントから保管先（S3互換）へ直接送る。

1. `POST /api/v1/assets/upload-url` — 受け入れ可能な Content-Type を確認し、アセットキー（UUIDv7＋拡張子。元のファイル名は使わない）と署名付きURLを払い出す。署名の宛先は**受け入れ前の置き場**で、配信パスではない
2. クライアントが署名付きURLへ実体を PUT する
3. `POST /api/v1/assets/{assetKey}/confirm` — 実体を検査し、配信対象として確定して公開配信URLを返す

確定時の検査は先頭バイト列の範囲取得1回で、サイズ上限・マジックバイトによる形式判定・払い出したキーの拡張子との一致を確認する（申告された Content-Type は信用しない）。検査に通らない実体は破棄して 400 を返す。

**クライアントが書き込めるのは受け入れ前の置き場だけ**で、配信対象へは確定（保管先内のコピー）でしか実体が入らない。署名付きURLの有効期限内に同じURLへ再送されても、確定済みの配信実体は変わらない。受け入れ前に残った実体はバケットのライフサイクルで期限切れにする。判断の理由は [DECISIONS.md](DECISIONS.md) 18。

配信は CloudFront の `/assets/*` 経由で、保管バケットは非公開のまま OAC で読み取る。受け入れ前の置き場は配信パスの外にあるため CDN から到達しない。

保存値をキーにする理由・音源を外部サービスに委ねる理由は [DECISIONS.md](DECISIONS.md)（7・9）。

