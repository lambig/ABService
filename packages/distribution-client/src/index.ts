import type { InstallationManifest } from 'abservice-installation';
import { buildClient } from './client';

declare const listenerToken: unique symbol;

/** 形だけを確かめた試聴端末の token。有効かどうかは配布 API への問い合わせで初めて分かる。 */
export type ListenerToken = string & { readonly [listenerToken]: true };

/** `abs_device_` と小文字 16 進 64 桁の形でなければ undefined。入力の前後の空白は呼び出し側で扱う。 */
export const parseListenerToken = (input: string): ListenerToken | undefined =>
  /^abs_device_[0-9a-f]{64}$/.test(input)
    ? (input as ListenerToken)
    : undefined;

/**
 * 取得失敗の分類。呼び出し側が次の操作（再認証・package の取り直し・asset の取り直し・再試行）を選べる粒度にする。
 * - unauthorized / forbidden: 配布 API が token を受け付けない（401 / 403）。期限切れと失効は応答で区別できない
 * - unavailable: 配布が無効（package API の 404）
 * - not-distributed: 音源が現在の package に無い（URL 解決の 404）。package が差し替わっている
 * - source-rejected: 音源の取得先・公開配信が 4xx を返した。署名 URL の期限切れを含む
 * - server / network: 5xx、または応答を受け取れなかった（CORS の拒否を含む）
 * - invalid-response: 応答が JSON でない・形が違う・Manifest が strict な検証に通らない
 * - unsupported-schema: 読めない schemaVersion の Manifest
 * - unsupported-asset: Manifest の中で音源とも表示素材とも参照されていない asset
 */
export type DistributionError =
  | 'unauthorized'
  | 'forbidden'
  | 'unavailable'
  | 'not-distributed'
  | 'source-rejected'
  | 'server'
  | 'network'
  | 'invalid-response'
  | 'unsupported-schema'
  | 'unsupported-asset'
  | 'aborted';

/** 失敗には分類と HTTP ステータスだけを載せる。token・署名 URL・応答本文は載せない。 */
export type DistributionResult<T> =
  | Readonly<{ kind: 'ok'; value: T }>
  | Readonly<{ kind: 'error'; error: DistributionError; status?: number }>;

/**
 * 配布元への接続。token はこの接続の中にだけ持ち、保存もログ出力もしない。
 * apiBase の既定は同じ origin、assetBase の既定は公開サイトと同じ `/assets`。
 */
export type DistributionConnection = Readonly<{
  token: ListenerToken;
  apiBase?: string;
  assetBase?: string;
  fetch?: typeof fetch;
}>;

/** 取得だけを行い、保存方式を知らない。asset の checksum は保存する側が照合する。 */
export type DistributionClient = Readonly<{
  /** 現在の配布 package を取得し、strict に検証した Manifest を返す。 */
  package: (
    signal: AbortSignal,
  ) => Promise<DistributionResult<InstallationManifest>>;
  /**
   * Manifest の中での参照のされ方で取得元を決める。音源は取得の直前に署名 URL を解決して配信元から直接取り、
   * 表示素材は公開配信から取る。
   */
  asset: (
    manifest: InstallationManifest,
    assetId: string,
    signal: AbortSignal,
  ) => Promise<DistributionResult<Blob>>;
}>;

/** 通信は操作のたびに行い、生成時には行わない。 */
export const createDistributionClient = (
  connection: DistributionConnection,
): DistributionClient => buildClient(connection);
