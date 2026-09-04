/*
 * 静的サイトへの要求経路を、S3 の実オブジェクトキーへ解決する（CloudFront Functions / viewer request）。
 *
 * OAC 経由の S3 は REST エンドポイントで、ディレクトリ索引を持たない。`default_root_object` も
 * 配信全体の直下にしか効かないため、`/albums/` や `/admin` はその綴りのままキーとして引かれ、
 * 403/404 になる。Astro の既定（build.format: 'directory'）は各ページを `index.html` として出す
 * ので、ここで綴りを合わせる。
 *
 * 結び付けるのは静的サイトの2つの振り分け（既定と `/admin*`）だけ。`/api/*` へ結ぶと
 * `/api/v1/albums` のような拡張子を持たない経路まで書き換えることになる。`/assets/*` のキーは
 * 確定済みで補完の余地がない。
 *
 * この実体は E2E の配信（`e2e/scripts/serve-app.mjs`）が読み込んで同じ要求へ適用する。写して
 * 並べると、片方を直したときにもう一方が黙って古くなり、検査が本番と違う解決で緑になる。
 *
 * CloudFront Functions の実行環境（cloudfront-js-2.0）はモジュールを持たない。`handler` を露出
 * する平らな1ファイルとして書く。
 */

var INDEX_DOCUMENT = 'index.html';

/* 最後の区間に `.` を含むものは実体を名指している（資産・`404.html` 等）ため素通しする */
function namesFile(uri) {
  var segments = uri.split('/');
  return segments[segments.length - 1].indexOf('.') !== -1;
}

function resolveStaticUri(uri) {
  var directory = uri.charAt(uri.length - 1) === '/' ? uri : uri + '/';
  return namesFile(uri) ? uri : directory + INDEX_DOCUMENT;
}

function handler(event) {
  var request = event.request;
  request.uri = resolveStaticUri(request.uri);
  return request;
}
