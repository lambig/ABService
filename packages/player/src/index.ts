import type { InstallationManifest } from "abservice-installation";
import { player } from "./player";

/** 保存方式から独立した音源取得。中止後の遅延完了もプレイヤー側で無効化する。 */
export type LocalAssetResolver = (
  assetId: string,
  signal: AbortSignal,
) => Promise<Blob>;

/** シーク中でない再生状態。 */
export type SettledPhase =
  "idle" | "loading" | "ready" | "playing" | "paused" | "ended" | "error";

/** シークが終わった後に戻る状態。再生中からのシークは再生へ、それ以外は一時停止へ戻る。 */
export type SeekResume = "playing" | "paused";

/** どの phase でも持つ項目。 */
export type SnapshotFacts = Readonly<{
  playbackItemId: string | null;
  /** canonicalな曲ID。作品クロスフェードには存在しない。 */
  trackId: string | null;
  position: number;
  duration: number;
  error: string | null;
}>;

/**
 * UIが表示する再生状態。時刻・長さはmedia実体の秒数で、Manifestの推定値を使わない。
 * phase で形が決まり、シーク中（`seeking`）だけがシーク後の戻り先 `resumeTo` を持つ。
 */
export type PlayerSnapshot = SnapshotFacts &
  (
    | Readonly<{ phase: SettledPhase }>
    | Readonly<{ phase: "seeking"; resumeTo: SeekResume }>
  );

/**
 * 表示と操作の可否に使う状態。シークは一瞬で終わるため、シーク中は戻り先の状態として扱い、表示をちらつかせない。
 * シークそのものを扱う側（試聴画面の状態の写し）は `phase` を直接見る。
 */
export const settledPhase = (state: PlayerSnapshot): SettledPhase =>
  state.phase === "seeking" ? state.resumeTo : state.phase;

/** 停止は音源を解放し、選択曲を保持する。再取得はselectで行う。dispose後は操作を受け付けない。 */
export type Player = Readonly<{
  snapshot: () => PlayerSnapshot;
  select: (playbackItemId: string) => Promise<void>;
  play: () => Promise<void>;
  pause: () => void;
  seek: (seconds: number) => void;
  stop: () => void;
  dispose: () => void;
}>;

/** 再生操作と同じ寿命を持つ音声接続。実装は障害を自身で処理し、再生操作へ例外を返さない。 */
export type PlaybackConnection = Readonly<{
  play: () => void;
  pause: () => void;
  seek: () => void;
}>;

/** mediaは選曲単位で借用し、abort時に接続を解放する。URLや音源の取得は所有しない。 */
export type ConnectPlayback = (
  media: HTMLAudioElement,
  signal: AbortSignal,
) => PlaybackConnection;

/**
 * 検証済みManifestとresolverを結ぶブラウザプレイヤー。自動再生・自動次曲送りはしない。
 * resolverとmedia要素の生存期間を選曲単位に閉じ、旧完了・旧イベントは通知しない。
 * onChangeは同期的な表示更新用。音響解析・永続保存・readinessの保証はこの境界の責務外。
 */
export const createPlayer = (
  manifest: InstallationManifest,
  resolve: LocalAssetResolver,
  onChange: (state: PlayerSnapshot) => void,
  connect?: ConnectPlayback,
): Player => player(manifest, resolve, onChange, connect);
