/**
 * スタックの所在と資格情報。
 *
 * 既定はローカル開発の構成（docker compose の DB + `quarkusDev` の backend + 静的配信）。
 * CI では環境変数で上書きする。
 */
export const stack = {
  /**
   * バックエンドの起点。シードと起動待ちに使う。
   *
   * 開発用（8080）とは別のポートに置く。E2E 用の backend は専用のデータベースを clean してから
   * 起動するため（#252）、開発用と同じポートにすると、E2E を回すたびに開発用を止めることになる。
   */
  backendBaseUrl: process.env['E2E_BACKEND_BASE_URL'] ?? 'http://localhost:8090',

  /** 公開サイトの起点。Playwright の baseURL と、配信の listen 先の両方がここから決まる */
  siteBaseUrl: process.env['E2E_SITE_BASE_URL'] ?? 'http://localhost:4321',

  /**
   * 管理画面の起点。
   *
   * 公開サイトと別のポートに置く。両方を同じ実行で見るため、配信を1つにまとめられない。ポートは
   * バックエンドの CORS が許すもの（`application.properties` の既定）と揃える。管理画面は
   * ブラウザから管理APIを叩くため、許されていないと画面が空のまま緑になる。
   *
   * 本番と同じ `/admin` 配下に置く（管理画面の Astro が `base` に宣言している）。ルートで配ると、
   * プレフィックスのある状態を一度も検査しないまま緑になり、資産の参照や画面間のリンクが本番だけで
   * 壊れる。
   */
  adminBaseUrl: process.env['E2E_ADMIN_BASE_URL'] ?? 'http://localhost:4322/admin',

  /** 管理APIの鍵。ローカルと CI は application.properties の開発既定と同じ値を使う */
  adminApiKey: process.env['ADMIN_API_KEY'] ?? 'dev-admin-api-key',

  /**
   * アセットの配信元。
   *
   * <p>
   * 本番は CloudFront の `/assets/*` ビヘイビアが S3 を読む（`infra/README.md`）。ローカルと CI には
   * CloudFront が無いため、配信（`scripts/serve-app.mjs`）が同じ経路を保管先へ取り次ぐ。取り次がないと、
   * カバー画像は**組み上がった側の不足ではなく配信の不足**で出ず、証跡に写らない。
   * </p>
   */
  assetOrigin: process.env['E2E_ASSET_ORIGIN'] ?? 'http://127.0.0.1:9000/abservice-assets',

  /**
   * アセットの配信パス。
   *
   * バックエンドの `abservice.assets.public-base-path` と、両フロントの `PUBLIC_ASSET_BASE_PATH` の
   * 既定に揃える。ずれると、画面が指す経路と配信が取り次ぐ経路が食い違う。
   */
  assetBasePath: '/assets',
} as const;

/** 配信するアプリ。組み上がった成果物の置き場と、listen する起点を対で持つ */
export const apps = {
  public: { distDir: 'frontend-public/dist', baseUrl: stack.siteBaseUrl },
  admin: { distDir: 'frontend-admin/dist', baseUrl: stack.adminBaseUrl },
} as const;

/** 配信するアプリの名前 */
export type AppName = keyof typeof apps;

/**
 * 配信が listen するポート。
 *
 * 起点の URL から導く。listen 先を別の環境変数にすると、URL だけを変えたときに「Playwright は
 * 新しい URL を待ち、配信は元のポートで上がる」というすれ違いが起きる。
 */
export const portOf = (app: AppName): number => Number(new URL(apps[app].baseUrl).port);

/**
 * 配信がプレフィックスとして受ける経路。
 *
 * <p>
 * 起点の URL から導く（`portOf` と同じ理由）。組み上がった成果物はプレフィックスを含まない平らな形で
 * 出るため、配信側が要求からこれを剥がしてファイルを引く。公開サイトは空文字になる。
 * </p>
 */
export const basePathOf = (app: AppName): string =>
  new URL(apps[app].baseUrl).pathname.replace(/\/$/u, '');
