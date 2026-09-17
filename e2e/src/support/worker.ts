import { test } from '@playwright/test';

/**
 * この worker の印。
 *
 * <p>
 * WORKER-OWNED-SCRATCH: 検査のためだけに作るもの（scratch）は、作った worker だけが片付ける（#430）。
 * 接頭辞に当たるものをぜんぶ消す片付けは、並列に走る別の worker がいま使っている scratch まで消す。
 * 名に worker の印を入れ、片付けもその印で拾うことで、所有の境界を名前そのものに持たせる。
 * </p>
 *
 * <p>
 * `parallelIndex` は 0 から worker 数 - 1 の値で、worker が落ちて立ち直っても同じ番号を引き継ぐ
 * （`workerIndex` は立ち直るたびに増える）。直列（1 worker）のときは常に `W0`。
 * </p>
 *
 * <p>
 * テストの実行中（`test` / `beforeEach` / `afterEach` の中）でしか呼べない。実行の外（`prepare-stack`）
 * は worker を持たないため、そこでの片付けは印を付けない接頭辞ぜんぶを対象にする。
 * </p>
 */
export const workerTag = (): string => `W${String(test.info().parallelIndex)}`;

/** サイト文言のキーに入れられる形（小文字の英数字）にした worker の印 */
export const workerKeySegment = (): string => workerTag().toLowerCase();
