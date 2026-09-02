/**
 * スタックの所在と資格情報。
 *
 * 既定はローカル開発の構成（docker compose の DB + `quarkusDev` の backend + 静的配信）。
 * CI では環境変数で上書きする。
 */
export const stack = {
  /** バックエンドの起点。シードと起動待ちに使う */
  backendBaseUrl: process.env['E2E_BACKEND_BASE_URL'] ?? 'http://localhost:8080',

  /** 公開サイトの起点。Playwright の baseURL と、配信の listen 先の両方がここから決まる */
  siteBaseUrl: process.env['E2E_SITE_BASE_URL'] ?? 'http://localhost:4321',

  /** 管理APIの鍵。ローカルと CI は application.properties の開発既定と同じ値を使う */
  adminApiKey: process.env['ADMIN_API_KEY'] ?? 'dev-admin-api-key',
} as const;

/**
 * 配信が listen するポート。
 *
 * 起点の URL から導く。listen 先を別の環境変数にすると、URL だけを変えたときに「Playwright は
 * 新しい URL を待ち、配信は元のポートで上がる」というすれ違いが起きる。
 */
export const sitePort = Number(new URL(stack.siteBaseUrl).port);
