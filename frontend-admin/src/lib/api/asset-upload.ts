import {
  confirmAsset,
  issueAssetUploadUrl,
  type AssetUploadUrl,
  type ConfirmedAsset,
} from './client';
import type { ApiResult } from './http';

/**
 * 画像を保管先へ送り、配信できる状態にするまでの手順。
 *
 * <p>
 * 3つの経路に分かれている——アップロード先の払い出し、保管先への送信、確定。**実体は管理APIを
 * 経由しない**（払い出された署名付きURLへブラウザから直接送る）ため、真ん中だけが管理APIではない。
 * 呼び出し側にこの順序を持たせると、送っただけで確定していない鍵を作品へ結び付ける経路が残る。
 * </p>
 */

/**
 * 保管先へ実体を送る。
 *
 * <p>
 * 失敗を `unauthorized` にしない。ここで返る 403 は署名の期限切れや条件の不一致であって、管理APIの
 * 鍵が断られたことではない。畳むと、画像を送り損ねただけで鍵の入力へ戻されることになる。
 * </p>
 *
 * <p>
 * 保管先は Problem Details を返さないため、位置つきの検証エラーも持たない。実体の検査は確定の段で
 * 行われる。
 * </p>
 */
const delivered = async (uploadUrl: string, file: File): Promise<ApiResult<void>> => {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  }).catch(() => null);

  return response === null
    ? { kind: 'failed', reason: 'network', message: '画像の保管先へ接続できません。' }
    : response.ok
      ? { kind: 'ok', value: undefined }
      : {
          kind: 'failed',
          reason: 'http',
          status: response.status,
          message: `画像を保管先へ送れませんでした（HTTP ${String(response.status)}）。`,
        };
};

const confirmDelivered = async (
  apiKey: string,
  file: File,
  issued: AssetUploadUrl,
): Promise<ApiResult<ConfirmedAsset>> => {
  const sent = await delivered(issued.uploadUrl, file);

  return sent.kind === 'ok' ? confirmAsset(apiKey, issued.assetKey) : sent;
};

/**
 * 画像を配信できる状態にする。
 *
 * <p>
 * 返るのは確定まで済んだアセットだけで、途中で止まったものは失敗として返る。**どの段で止まっても
 * 鍵は返らない**——送っただけの実体は配信されず、それを作品へ結び付けると画像の出ない作品になる。
 * </p>
 *
 * @param apiKey 管理APIの鍵
 * @param file 選ばれた画像。申告された形式（`type`）をそのまま払い出しへ渡す
 */
export const uploadAsset = async (
  apiKey: string,
  file: File,
): Promise<ApiResult<ConfirmedAsset>> => {
  const issued = await issueAssetUploadUrl(apiKey, file.type);

  return issued.kind === 'ok' ? confirmDelivered(apiKey, file, issued.value) : issued;
};
