import type { InstallationManifest } from "abservice-installation";
import { player } from "./player";

/** 保存方式から独立した音源取得。中止後の遅延完了もプレイヤー側で無効化する。 */
export type LocalAssetResolver = (
  assetId: string,
  signal: AbortSignal,
) => Promise<Blob>;

/** UIが表示する再生状態。時刻・長さはmedia実体の秒数で、Manifestの推定値を使わない。 */
export type PlayerSnapshot = Readonly<{
  phase:
    "idle" | "loading" | "ready" | "playing" | "paused" | "ended" | "error";
  trackId: string | null;
  position: number;
  duration: number;
  error: string | null;
}>;

/** 停止は音源を解放し、選択曲を保持する。再取得はselectで行う。dispose後は操作を受け付けない。 */
export type Player = Readonly<{
  snapshot: () => PlayerSnapshot;
  select: (trackId: string) => Promise<void>;
  play: () => Promise<void>;
  pause: () => void;
  seek: (seconds: number) => void;
  stop: () => void;
  dispose: () => void;
}>;

/**
 * 検証済みManifestとresolverを結ぶブラウザプレイヤー。自動再生・自動次曲送りはしない。
 * resolverとmedia要素の生存期間を選曲単位に閉じ、旧完了・旧イベントは通知しない。
 * onChangeは同期的な表示更新用。音響解析・永続保存・readinessの保証はこの境界の責務外。
 */
export const createPlayer = (
  manifest: InstallationManifest,
  resolve: LocalAssetResolver,
  onChange: (state: PlayerSnapshot) => void,
): Player => player(manifest, resolve, onChange);
