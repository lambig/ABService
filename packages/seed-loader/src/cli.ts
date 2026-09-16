import { parseArgs } from 'node:util';
import { adminApi } from 'abservice-admin-api';
import { applyPlan } from './apply.ts';
import { SeedFileError } from './decode.ts';
import { describePlan, planSeed, takeSnapshot } from './plan.ts';
import { readSeedDirectory } from './seed-files.ts';

/**
 * 初期データを管理API経由で投入する（#373）。
 *
 * ```
 * SEED_API_BASE_URL=https://<配信のドメイン> ADMIN_API_KEY=<鍵> \
 *   node packages/seed-loader/src/cli.ts --dir <投入ディレクトリ> [--dry-run]
 * ```
 *
 * <p>
 * `--dry-run` は投入ファイルの形を確かめ、投入先の今の状態と突き合わせた計画を出して終わる。書き込みは
 * 送らない。実行のときも同じ計画を先に出す。
 * </p>
 *
 * <p>
 * 終了コードは、成功が 0、投入ファイルの誤り・計画の問題・投入の失敗が 1、引数や環境変数の不足が 2。
 * </p>
 */

const USAGE = [
  '使い方: node src/cli.ts --dir <投入ディレクトリ> [--dry-run]',
  '環境変数: SEED_API_BASE_URL（バックエンドの起点）, ADMIN_API_KEY（管理APIの鍵）',
].join('\n');

const usageError = (message: string): never => {
  console.error(message);
  console.error(USAGE);
  process.exit(2);
};

const required = (name: string): string =>
  process.env[name] ?? usageError(`${name} が設定されていません`);

const { values } = parseArgs({
  options: {
    dir: { type: 'string' },
    'dry-run': { type: 'boolean', default: false },
  },
  strict: true,
});

const directory = values.dir ?? usageError('--dir が指定されていません');
const dryRun = values['dry-run'];
const baseUrl = required('SEED_API_BASE_URL');
const apiKey = required('ADMIN_API_KEY');

const run = async (): Promise<void> => {
  const seed = readSeedDirectory(directory);
  console.log(`投入内容: ${seed.directory}`);
  console.log(`投入先: ${baseUrl}`);

  const api = adminApi({ baseUrl, apiKey });
  const plan = planSeed(seed, await takeSnapshot(api, seed));
  console.log('');
  console.log('計画');
  describePlan(plan).forEach((line) => {
    console.log(line);
  });
  console.log('');

  const outcome = dryRun
    ? Promise.resolve('dry-run のため書き込みは送っていません')
    : applyPlan(plan, seed, api, (line) => {
        console.log(line);
      }).then(
        (summary) =>
          `完了: 作成 ${String(summary.created)} / 公開 ${String(summary.published)} / 画像 ${String(summary.coverImagesAdded)} / 飛ばした ${String(summary.skipped)}`,
      );
  console.log(await outcome);
};

await run().catch((error: unknown) => {
  console.error(
    error instanceof SeedFileError
      ? `投入ファイルの誤り\n${error.message}`
      : error instanceof Error
        ? error.message
        : String(error),
  );
  /* eslint-disable-next-line functional/immutable-data -- 終了コードは process の属性への代入でしか伝えられない（process.exit は書き出し途中の出力を待たない） */
  process.exitCode = 1;
});
