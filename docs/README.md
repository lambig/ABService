# ABService Documentation

## 概要

ABServiceは、モノリポジトリ構成で構築されたWebサービスです。

## ドキュメント記述規約

`docs/` 配下の全ドキュメント（および設計・仕様を記述する Markdown 全般）に適用する共通ルール:

- **状態を現在形で記述する**。「いま何がどうなっているか」を書く（例: 「combinator は実装済み」）。
- **作業ログ（実施イベント）を残さない**。`✅完了` / `〜を追加した` / `〜を実装した` / header等への「（フェーズX完了を反映）」といったイベント記録は書かない。**何をいつ実施したかの正は git コミット履歴**とする。
- **同じ事実を複数箇所に重複記載しない**。完了は該当箇所の状態記述を1箇所だけ更新し、残タスク一覧の完了項目は「完了」マークではなく**削除**する。

## ドキュメント一覧

### 🚀 再開時にまず読む

- **[STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md)** - 開発状況と再開ロードマップ（レイヤー別の検証済み現状・全残タスク棚卸し・優先度付き計画・ArchUnit導入計画）。**進捗の正はこのドキュメント**

### 設計ドキュメント

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - システムアーキテクチャ
- **[DATABASE_DESIGN.md](DATABASE_DESIGN.md)** - データベース設計
- **[DOMAIN_MODEL_DESIGN.md](DOMAIN_MODEL_DESIGN.md)** - ドメインモデル設計
- **[RESULT_TYPE_GUIDE.md](RESULT_TYPE_GUIDE.md)** - Result型の使用ガイド

### 実装ガイド

- **[CODING_GUIDELINES.md](CODING_GUIDELINES.md)** - コーディングガイドライン（強制ルールは STATUS_AND_ROADMAP.md §7 と各設定が正）
- **[REPOSITORY_IMPLEMENTATION.md](REPOSITORY_IMPLEMENTATION.md)** - リポジトリ実装ガイド
- **[ID_DESIGN_POLICY.md](ID_DESIGN_POLICY.md)** - ID設計ポリシー
- **[AUDIT_COLUMNS.md](AUDIT_COLUMNS.md)** - 監査カラム設計

### 設計判断の記録

- **[VO_REFACTORING.md](VO_REFACTORING.md)** - ArtistCredit / Event を集約ルートにせず VO 埋め込みとした判断と根拠

## アーキテクチャ

### 技術スタック

- **バックエンド**: Quarkus (Java 25+) / Gradle
- **フロントエンド管理画面**: Svelte
- **フロントエンド公開画面**: Svelte + Astro
- **データベース**: PostgreSQL
- **マイグレーション**: Flyway
- **データアクセス**: Hibernate Reactive Panache（Mutiny）
- **認証・認可**: OIDC + Keycloak + JWT
- **コンテナ**: Docker & Docker Compose

詳細なアーキテクチャ設計については、[ARCHITECTURE.md](ARCHITECTURE.md)を参照してください。

### システム構成

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
```

## 開発環境セットアップ

### 前提条件

- Java 25+
- Node.js 18+
- Docker & Docker Compose
- Git

### セットアップ手順

1. **リポジトリのクローン**
   ```bash
   git clone <repository-url>
   cd ABService
   ```

2. **自動セットアップ**
   ```bash
   ./scripts/setup.sh
   ```

3. **手動セットアップ（オプション）**
   ```bash
   # ルート依存関係のインストール
   npm install
   
   # バックエンドのセットアップ
   cd backend
   ./gradlew build -x test
   cd ..
   
   # フロントエンド管理画面のセットアップ
   cd frontend-admin
   npm install
   cd ..
   
   # フロントエンド公開画面のセットアップ
   cd frontend-public
   npm install
   cd ..
   
   # Docker環境のセットアップ
   docker-compose build
   ```

## 開発

### 開発環境の起動

#### 全サービス一括起動
```bash
npm run dev
# または
./scripts/dev.sh
```

#### 個別サービス起動
```bash
# バックエンドのみ
npm run dev:backend

# 管理画面のみ
npm run dev:admin

# 公開画面のみ
npm run dev:public
```

#### Docker環境での起動
```bash
npm run docker:up
```

### 利用可能なコマンド

#### ビルド
```bash
# 全サービス
npm run build

# 個別サービス
npm run build:backend
npm run build:admin
npm run build:public
```

#### テスト
```bash
# 全サービス
npm run test

# 個別サービス
npm run test:backend
npm run test:admin
npm run test:public
```

#### リント・フォーマット
```bash
# リント
npm run lint

# フォーマット
npm run format
```

#### クリーンアップ
```bash
# 全サービス
npm run clean

# 個別サービス
npm run clean:backend
npm run clean:admin
npm run clean:public
```

## サービスURL

開発環境では以下のURLでアクセスできます：

- **バックエンドAPI**: http://localhost:8080
- **管理画面**: http://localhost:5173
- **公開画面**: http://localhost:4321
- **PostgreSQL**: localhost:5432

## ディレクトリ構造

```
ABService/
├── backend/                 # Quarkusバックエンド
│   ├── src/
│   ├── build.gradle
│   └── gradlew
├── frontend-admin/          # Svelte管理画面
│   ├── src/
│   ├── package.json
│   └── vite.config.js
├── frontend-public/         # Svelte + Astro公開画面
│   ├── src/
│   ├── package.json
│   └── astro.config.mjs
├── docker/                  # Docker設定
│   ├── docker-compose.yml
│   └── Dockerfile.*
├── docs/                    # ドキュメント
│   └── README.md
├── scripts/                 # 開発用スクリプト
│   ├── setup.sh
│   └── dev.sh
├── package.json             # ルートpackage.json
├── .gitignore
└── CONTRIBUTION.md
```

## コントリビューション

開発に参加する前に、[CONTRIBUTION.md](../CONTRIBUTION.md)を必ずお読みください。

## トラブルシューティング

### よくある問題

1. **ポートが既に使用されている**
   - 他のプロセスが同じポートを使用している可能性があります
   - `lsof -i :ポート番号` で確認し、必要に応じてプロセスを終了してください

2. **依存関係のインストールエラー**
   - Node.jsのバージョンを確認してください（18以上が必要）
   - `npm cache clean --force` でキャッシュをクリアしてください

3. **Docker関連のエラー**
   - Dockerが起動していることを確認してください
   - `docker system prune` で不要なリソースをクリアしてください

### ログの確認

各サービスのログは以下の場所で確認できます：

- **バックエンド**: コンソール出力
- **フロントエンド**: ブラウザの開発者ツール
- **Docker**: `docker-compose logs [service-name]`
