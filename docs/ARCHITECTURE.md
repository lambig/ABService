# ABService アーキテクチャ設計書

## 概要

ABServiceは、モノリポジトリ構成で構築されたWebサービスです。バックエンドはQuarkus、フロントエンドは管理画面をSvelte、公開画面をSvelte + Astroで構築します。

## 技術スタック

### バックエンド
- **フレームワーク**: Quarkus 3.x
- **Java**: 25+
- **データベース**: PostgreSQL
- **データアクセス**: Blaze-Persistence (JPA拡張)
- **マイグレーション**: Flyway
- **認証・認可**:
  - Quarkus Security（自作の APIキー認証メカニズム + `@RolesAllowed`）
- **オブジェクトストレージ**: S3互換（本番はS3、開発はMinIO。quarkus-amazon-s3）
- **API**: RESTEasy Reactive
- **設定管理**: Quarkus Configuration
- **テスト**: JUnit 5 + REST Assured

### フロントエンド
- **管理画面**: Svelte + SvelteKit
- **公開画面**: Svelte + Astro
- **共通**: TypeScript, ESLint, Prettier

### インフラ・開発環境
- **コンテナ**: Docker & Docker Compose
- **データベース**: PostgreSQL

## システム構成

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Frontend Admin │    │ Frontend Public │    │    Backend      │
│     (Svelte)    │    │ (Svelte + Astro)│    │   (Quarkus)     │
│   Port: 5173    │    │   Port: 4321    │    │   Port: 8080    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   PostgreSQL    │
                    │   Port: 5432    │
                    └─────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Keycloak      │
                    │   Port: 8180    │
                    └─────────────────┘
```

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
- **認証必須**: 全集約の `*CommandResource`（作成・更新・削除・公開/非公開）、管理向けQuery（`/api/v1/admin/**`）、公開サイトが参照しないマスタ系Query（`/api/v1/tunes`・`/api/v1/album-articles`）
- **認証不要**: 公開向けQuery（`/api/v1/albums`・`/api/v1/articles`）。`Audience.PUBLIC` として公開中のものだけを返し、下書きは未存在として扱う
- **強制**: ArchUnit で `*CommandResource` / `*AdminQueryResource` への `@RolesAllowed` 付与を必須にする

### セキュリティフロー
1. クライアント（管理画面・運用者）が `Authorization: Bearer <APIキー>` を付けてバックエンドAPIを呼ぶ
2. 認証メカニズムがキーを抽出し、IdentityProvider が設定値と照合して管理者ロールを付与する
3. `@RolesAllowed` が付いたエンドポイントはロールを検査し、公開向けQueryは匿名のまま処理する
4. 未認証・キー不正は 401（`WWW-Authenticate: Bearer realm="abservice"` 付き）、権限不足は 403 を、いずれも RFC 9457 Problem Details（`urn:abservice:error:UNAUTHORIZED` / `FORBIDDEN`）で返す

## データアクセス層

### ドメインモデル設計

ABServiceのバックエンドは、ドメイン駆動設計（DDD）の原則に基づいて設計されています。

#### ドメインオブジェクト階層

```
DomainObject<T>
├── ValueObject<T>              // 値で識別されるオブジェクト
└── DomainEntity<T, ID>         // IDで識別されるオブジェクト
    └── Aggregate<T, ID>        // 永続化境界を持つエンティティ
```

#### 値オブジェクト（Value Object）

- **不変性**: 一度生成されたら状態を変更できない
- **等価性**: すべての属性が等しければ等価
- **副作用なし**: メソッド実行が状態を変更しない
- **実装**: Java Records推奨（Lombokは不要、Recordで十分）

#### エンティティ（Entity）

- **同一性**: 一意な識別子（ID）によって区別
- **不変更新パターン**: Lombok `@With(AccessLevel.PRIVATE)`による状態変更（新しいインスタンスを返す）
- **Setterの禁止**: 不変性を保つ
- **実装**: Lombok `@With(AccessLevel.PRIVATE)`, `@Getter`, `@AllArgsConstructor`, `@EqualsAndHashCode`を推奨
- **原則**: witherメソッドはprivateにし、業務的な意味を持つpublicメソッドを提供すること

#### 集約（Aggregate）

- **整合性境界**: トランザクション境界
- **永続化単位**: Repositoryは集約ルートにのみ提供
- **ID参照**: 集約間はIDで参照（オブジェクト参照禁止）

### 業務日付/日時

#### BusinessDate / BusinessDateTime

- **タイムゾーン**: Asia/Tokyo固定
- **内部表現**: LocalDate / Instant
- **テスト容易性**: BusinessDateTimeProviderで抽象化

#### BusinessDateTimeProvider

- **役割**: 現在時刻の取得を抽象化
- **使用箇所**: ApplicationService/DomainServiceのみ
- **実装**:
  - `SystemBusinessDateTimeProvider`: 本番用（リアルタイム）
  - テスト用実装（固定時刻）も追加可能

### DomainServiceとFactory

#### DomainService

- **適用パターン**:
  - 複数集約の協調
  - 一意性チェック
  - 複雑な計算
- **原則**: ステートレス、リアクティブ（Uni<T>返却）

#### Factory

- **役割**: 複雑な集約の生成ロジックをカプセル化
- **適用**: 外部依存を伴う生成、複雑なバリデーション

### Blaze-Persistence採用理由
- **高度なクエリ機能**: JPAのCriteria APIを拡張
- **エンティティビュー**: DTOの効率的なマッピング
- **パフォーマンス最適化**: 必要なデータのみを取得
- **動的クエリ**: 複雑な検索条件の動的構築

### データベース設計方針
- **正規化**: 第3正規形を基本とする
- **インデックス**: パフォーマンス要件に応じて適切に配置
- **マイグレーション**: Flywayによるバージョン管理
- **バックアップ**: 定期的なバックアップとリストア手順

## API設計

### RESTful API原則
- **リソース指向**: URLでリソースを表現
- **HTTPメソッド**: GET, POST, PUT, DELETEの適切な使用
- **ステータスコード**: 標準的なHTTPステータスコード
- **エラーハンドリング**: 統一されたエラーレスポンス形式

### API仕様
- **OpenAPI**: Swagger UIによるAPI仕様書
- **バージョニング**: URLパスでのバージョン管理
- **認証**: `Authorization: Bearer <APIキー>`（管理操作のみ。認証・認可アーキテクチャの節を参照）
- **レート制限**: 必要に応じて実装

### アセット（画像）のアップロードと配信
- **アップロード**: 3ステップ。`POST /api/v1/assets/upload-url` で署名付きURLとアセットキーを得て、クライアントがそのURLへ実体を直接 PUT し、`POST /api/v1/assets/{assetKey}/confirm` で確定する。実体は backend を経由しない
- **検証**: 確定時に先頭バイト列の範囲取得1回で、サイズ上限・マジックバイトによる形式判定・払い出したキーの拡張子との一致を確認する。申告された Content-Type は信用しない。検査に通らない実体は保管先から削除して 400 を返す
- **受け入れ形式**: JPEG / PNG / WebP。キーは UUIDv7 + 拡張子で、元のファイル名は使わない
- **配信**: CloudFront の `/assets/*` 経由（保管バケットは非公開のまま OAC で読み取る）
- **集約が持つ値**: 配信URLではなくアセットキー（`AssetKey` VO）。配信URLは照会時に配信ベースパスと組み合わせて Read Model 側で組み立てるため、CDNのパス構成やドメインの違いに保存データが依存しない。登録・更新APIはキーを受け取り、照会APIはURLを返す
- **音源**: 自前ホストせず外部サービス（SoundCloud）の埋め込みに委ねる（転送コストと運用の観点）

## フロントエンド設計

### 管理画面 (Svelte)
- **SPA**: シングルページアプリケーション
- **状態管理**: Svelte Store
- **ルーティング**: SvelteKit
- **UIコンポーネント**: 再利用可能なコンポーネント設計

### 公開画面 (Svelte + Astro)
- **SSG/SSR**: Astroによる静的サイト生成
- **パフォーマンス**: 最適化されたバンドルサイズ
- **SEO**: メタタグと構造化データ
- **アクセシビリティ**: WCAG 2.1準拠

## 開発・運用

### 開発環境
- **ローカル開発**: Docker Compose
- **ホットリロード**: 全サービス対応
- **デバッグ**: 各サービス個別デバッグ対応

### テスト戦略
- **単体テスト**: 各コンポーネントのテスト
- **統合テスト**: APIエンドポイントのテスト
- **E2Eテスト**: ユーザーシナリオのテスト
- **パフォーマンステスト**: 負荷テスト

### デプロイメント
- **コンテナ化**: Dockerイメージ
- **CI/CD**: GitHub Actions
- **環境**: 開発、ステージング、本番
- **監視**: ログ、メトリクス、アラート

## セキュリティ考慮事項

### データ保護
- **暗号化**: 通信時（TLS）と保存時（AES）
- **個人情報**: GDPR準拠
- **アクセスログ**: 監査証跡の保持

### 脆弱性対策
- **依存関係**: 定期的な脆弱性スキャン
- **セキュリティヘッダー**: CSP, HSTS等の設定
- **入力検証**: 全入力の適切な検証

## パフォーマンス要件

### レスポンス時間
- **API**: 95%ile < 200ms
- **ページロード**: 初回 < 3秒、以降 < 1秒
- **データベース**: クエリ < 100ms

### スケーラビリティ
- **水平スケーリング**: コンテナベース
- **キャッシュ**: Redis導入検討
- **CDN**: 静的リソース配信

## 今後の拡張計画

### マイクロサービス化
- **サービス分割**: ドメイン駆動設計
- **API Gateway**: 統一エントリーポイント
- **サービスメッシュ**: Istio導入検討

### 機能拡張
- **リアルタイム通信**: WebSocket
- **通知機能**: メール、プッシュ通知
