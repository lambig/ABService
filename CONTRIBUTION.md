# コントリビューションガイドライン

## 開発ルール

### 実装承認プロセス

本プロジェクトでは、**すべての実装実行前に必ず実装案の提示を行い、承認を受ける必要があります**。

#### 実装案の要件

実装案には以下の内容を含める必要があります：

1. **実装概要**
   - 実装する機能の概要
   - 技術的なアプローチ
   - 影響範囲

2. **技術仕様**
   - 使用する技術スタック
   - アーキテクチャの変更点
   - データベーススキーマの変更（該当する場合）

3. **実装手順**
   - 段階的な実装計画
   - 各段階での成果物
   - テスト計画

4. **リスク評価**
   - 想定されるリスク
   - 回避策・軽減策

#### 承認プロセス

1. 実装案を提示
2. レビュー・フィードバック
3. 承認
4. 実装実行

**承認なしでの実装実行は禁止されています。**

## プロジェクト構成

### アーキテクチャ

- **バックエンド**: Quarkus (Java - Amazon Corretto)
- **データベース**: PostgreSQL
- **マイグレーション**: Flyway
- **データアクセス**: Hibernate Reactive Panache（Mutiny）
- **認証・認可**: APIキー（`Authorization: Bearer`）+ Quarkus Security の `@RolesAllowed`
- **フロントエンド管理画面**: Svelte
- **フロントエンド公開画面**: Svelte + Astro
- **構成**: モノリポジトリ

### 技術スタックの固定と昇格

Java（Amazon Corretto）・Quarkus・Gradle は固定バージョンで運用し、**個別の作業の都合で勝手に変更しない**（ビルドが通るかどうかとは別に、拡張・静的解析ツールの対応バージョンが連鎖するため）。実際の版は `backend/gradle.properties`・`backend/build.gradle`・`backend/gradle/wrapper/gradle-wrapper.properties` が正。

一方で**固定は無期限ではない**。以下のいずれかに該当したら昇格を検討する（判断と昇格トリガの記録は [DECISIONS.md](docs/DECISIONS.md)、方針の策定は issue #158）。

- 使用中の系列が保守対象から外れた（パッチが出なくなった）
- 上流が対応を表明する範囲から外れた（例: JDK の対応上限を超えた）
- 依存する拡張・ツールが上位バージョンを要求する

昇格時は「Quarkus 本体・quarkiverse 拡張・AWS SDK・ErrorProne+NullAway」を揃えて上げ、`check`（静的解析・単体・実DB統合テスト）→ `quarkusBuild` → コンテナイメージビルドまで通すこと。

詳細なアーキテクチャ設計については、[ARCHITECTURE.md](docs/ARCHITECTURE.md)を参照してください。

### ディレクトリ構造

```
ABService/
├── backend/                 # Quarkusバックエンド
├── frontend-admin/          # Svelte管理画面
├── frontend-public/         # Svelte + Astro公開画面
├── docker/                  # Docker設定
├── docs/                    # ドキュメント
└── scripts/                 # 開発用スクリプト
```

## 開発環境

### 必要な環境

- Java 25 (Amazon Corretto 25 - 固定)
- Node.js（版は `.nvmrc` が正。Active LTS の最新メジャーに固定する。`nvm use` で従う）
- Docker & Docker Compose
- Git

### セットアップ手順

1. リポジトリのクローン
2. 開発環境の起動: `docker-compose up -d`
3. 各サービスの個別起動（必要に応じて）

## コーディング規約

### バックエンド (Quarkus)

- Java コーディング規約に準拠
- テストカバレッジ80%以上を維持
- APIドキュメント（OpenAPI）を必ず更新

### フロントエンド

- ESLint + Prettierを使用
- TypeScriptの型安全性を重視
- コンポーネントの再利用性を考慮

## コミット規約

```
<type>(<scope>): <description>

<body>

<footer>
```

### Type

- `feat`: 新機能
- `fix`: バグ修正
- `docs`: ドキュメント更新
- `style`: コードスタイル修正
- `refactor`: リファクタリング
- `test`: テスト追加・修正
- `chore`: ビルド・設定変更

## プルリクエスト

### 必須項目

1. 実装案の承認記録
2. テストの実行・通過
3. ドキュメントの更新
4. レビュアーの指定

### レビュー基準

- コードの品質
- テストカバレッジ
- パフォーマンスへの影響
- セキュリティ要件の遵守

## 質問・相談

実装案の作成や技術的な質問については、Issueを作成して相談してください。
