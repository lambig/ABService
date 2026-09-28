import { z } from 'zod';
import { id } from './common';
import {
  distributedAlbumV2,
  manifestV2Object,
  withDistributedChecks,
} from './v2';

/**
 * 作品の表示情報。canonical な Album の事実だけを持ち、layout・scene・effect は持たない。
 * 値の無い任意項目はキーごと省く（null を受け付けない）。空文字列は canonical な値として受け付ける。
 */
const presentationShape = {
  artistDisplayName: id,
  releaseDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional(),
  catalogNumber: z.string().optional(),
  description: z.string().optional(),
  descriptionFormat: z.enum(['PLAIN_TEXT', 'MARKDOWN']).optional(),
  originalWorkNote: z.string().optional(),
};

const distributedAlbumV3 = distributedAlbumV2
  .extend(presentationShape)
  .strict()
  .refine(
    (album) =>
      (album.description === undefined) ===
      (album.descriptionFormat === undefined),
    'description and descriptionFormat must be given together',
  )
  .readonly();

export const manifestV3Schema = withDistributedChecks(
  manifestV2Object.extend({
    schemaVersion: z.literal(3),
    albums: z.array(distributedAlbumV3).readonly(),
  }),
  'schema v3',
);

/**
 * schema v3: v2 の Album に作品の表示情報（名義・リリース日・カタログ番号・説明文の原文と形式・原作の出典）を足した
 * 配布 snapshot。説明文は HTML にせず原文のまま持ち、描画は端末側が担う。本文中の差し込み画像は asset に含めない。
 */
export type InstallationManifestV3 = z.infer<typeof manifestV3Schema>;
