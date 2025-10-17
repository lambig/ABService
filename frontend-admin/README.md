# ABService Frontend Admin

ABServiceの管理画面フロントエンドです。SvelteKitとTypeScriptを使用して構築されています。

## 技術スタック

- **フレームワーク**: SvelteKit
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
cd frontend-admin
npm install
```

### 3. 開発サーバーの起動

```bash
npm run dev
```

開発サーバーは http://localhost:5173 で起動します。

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
frontend-admin/
├── src/
│   ├── lib/
│   │   ├── components/     # 再利用可能なコンポーネント
│   │   ├── stores/         # Svelteストア
│   │   ├── utils/          # ユーティリティ関数
│   │   └── types/          # TypeScript型定義
│   ├── routes/             # ページルート
│   ├── app.html           # HTMLテンプレート
│   └── app.css            # グローバルスタイル
├── static/                # 静的ファイル
├── tests/                 # テストファイル
├── package.json
├── svelte.config.js
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.js
└── README.md
```

## 設定ファイル

### SvelteKit設定 (svelte.config.js)
- アダプター設定
- エイリアス設定
- プリプロセッサー設定

### Vite設定 (vite.config.ts)
- 開発サーバー設定
- プロキシ設定（API接続）
- テスト設定

### TypeScript設定 (tsconfig.json)
- コンパイラーオプション
- パスエイリアス
- 厳密な型チェック

### Tailwind CSS設定 (tailwind.config.js)
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
- 再利用可能なコンポーネントを作成
- Propsの型定義を明確にする
- アクセシビリティを考慮する

### 状態管理
- Svelteストアを使用してグローバル状態を管理
- ローカル状態はコンポーネント内で管理

### スタイリング
- Tailwind CSSのユーティリティクラスを使用
- カスタムコンポーネントクラスは`@layer components`で定義
- レスポンシブデザインを考慮

### API通信
- `src/lib/utils/api.ts`のAPIクライアントを使用
- エラーハンドリングを適切に実装
- 型安全性を保つ

## デプロイメント

### 本番ビルド

```bash
npm run build
```

ビルドされたファイルは`build/`ディレクトリに出力されます。

### 環境変数

```bash
# .env.local
VITE_API_BASE_URL=https://api.abservice.com
VITE_APP_NAME=ABService Admin
```

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
