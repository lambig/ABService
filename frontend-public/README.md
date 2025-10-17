# ABService Frontend Public

ABServiceの公開画面フロントエンドです。AstroとSvelteを使用して構築されています。

## 技術スタック

- **フレームワーク**: Astro
- **UI**: Svelte
- **言語**: TypeScript
- **スタイリング**: Tailwind CSS
- **アイコン**: Lucide Svelte
- **セキュリティ**: safe-chain
- **ビルドツール**: Vite
- **テスト**: Vitest + Playwright

## 前提条件

- Node.js 18+ (LTS推奨)
- npm 8+

## セットアップ

### 1. Node.jsバージョンの設定

```bash
# nvmを使用してNode.jsの最新LTSバージョンをインストール
nvm install --lts
nvm use --lts

# プロジェクトディレクトリでNode.jsバージョンを確認
nvm use
```

### 2. 依存関係のインストール

```bash
cd frontend-public
npm install
```

### 3. 開発サーバーの起動

```bash
npm run dev
```

開発サーバーは http://localhost:4321 で起動します。

## 利用可能なコマンド

### 開発

```bash
# 開発サーバーの起動
npm run dev

# プレビューモード
npm run preview

# 型チェック
npm run check

# 型チェック（監視モード）
npm run check:watch
```

### ビルド

```bash
# 本番用ビルド
npm run build
```

### テスト

```bash
# 全テストの実行
npm run test

# 単体テストのみ
npm run test:unit

# 単体テスト（監視モード）
npm run test:unit:watch

# 統合テストのみ
npm run test:integration
```

### コード品質

```bash
# リント
npm run lint

# フォーマット
npm run format
```

## プロジェクト構造

```
frontend-public/
├── src/
│   ├── components/        # 再利用可能なコンポーネント
│   ├── layouts/          # レイアウトコンポーネント
│   ├── pages/            # ページルート
│   ├── utils/            # ユーティリティ関数
│   └── types/            # TypeScript型定義
├── public/               # 静的ファイル
├── tests/                # テストファイル
├── package.json
├── astro.config.mjs
├── tsconfig.json
├── tailwind.config.mjs
└── README.md
```

## 設定ファイル

### Astro設定 (astro.config.mjs)
- インテグレーション設定（Svelte、Tailwind）
- 出力設定（静的サイト生成）
- 開発サーバー設定
- プロキシ設定（API接続）

### TypeScript設定 (tsconfig.json)
- コンパイラーオプション
- パスエイリアス
- 厳密な型チェック

### Tailwind CSS設定 (tailwind.config.mjs)
- カスタムカラーパレット
- フォント設定
- プラグイン設定

## セキュリティ

### safe-chain
このプロジェクトでは、npmパッケージのセキュリティを向上させるためにsafe-chainを使用しています。

```typescript
// 初期化例
import { default as safeChain } from 'safe-chain';
await safeChain.init();
```

## 開発ガイドライン

### コンポーネント設計
- AstroコンポーネントとSvelteコンポーネントを適切に使い分け
- 再利用可能なコンポーネントを作成
- Propsの型定義を明確にする
- アクセシビリティを考慮する

### ページ設計
- 静的サイト生成（SSG）を活用
- SEO最適化を考慮
- パフォーマンスを重視

### スタイリング
- Tailwind CSSのユーティリティクラスを使用
- レスポンシブデザインを考慮
- ダークモード対応（必要に応じて）

### API通信
- `src/utils/api.ts`のAPIクライアントを使用
- エラーハンドリングを適切に実装
- 型安全性を保つ

## ページ構成

### 主要ページ
- `/` - ホームページ
- `/features` - 機能紹介
- `/pricing` - 料金プラン
- `/about` - 会社概要
- `/contact` - お問い合わせ
- `/blog` - ブログ
- `/blog/[slug]` - ブログ記事詳細

### レイアウト
- `Layout.astro` - 基本レイアウト
- ヘッダー、フッター、ナビゲーション
- SEOメタタグの設定

## デプロイメント

### 本番ビルド

```bash
npm run build
```

ビルドされたファイルは`dist/`ディレクトリに出力されます。

### 静的サイトホスティング
- Netlify
- Vercel
- GitHub Pages
- AWS S3 + CloudFront

### 環境変数

```bash
# .env.local
PUBLIC_API_BASE_URL=https://api.abservice.com
PUBLIC_APP_NAME=ABService
PUBLIC_APP_URL=https://abservice.com
```

## パフォーマンス最適化

### 画像最適化
- Astroの画像最適化機能を使用
- WebP形式の活用
- 遅延読み込みの実装

### コード分割
- 動的インポートの活用
- 必要なコンポーネントのみ読み込み

### SEO最適化
- メタタグの適切な設定
- 構造化データの実装
- サイトマップの生成

## トラブルシューティング

### よくある問題

1. **Node.jsバージョンの不一致**
   ```bash
   nvm use
   ```

2. **依存関係のインストールエラー**
   ```bash
   rm -rf node_modules package-lock.json
   npm install
   ```

3. **型エラー**
   ```bash
   npm run check
   ```

4. **ビルドエラー**
   ```bash
   npm run build
   ```

### ログの確認

開発サーバーのログはターミナルに表示されます。ブラウザの開発者ツールでもエラーを確認できます。

## コントリビューション

開発に参加する前に、[CONTRIBUTION.md](../../CONTRIBUTION.md)を必ずお読みください。

### コミット規約

```
<type>(<scope>): <description>

<body>

<footer>
```

### プルリクエスト

1. 実装案の承認記録
2. テストの実行・通過
3. ドキュメントの更新
4. レビュアーの指定
