import { z } from 'zod';
import { asset, bytes, compare, id, packageShape, unique } from './common';

const metadataTrack = z
  .object({
    trackId: id,
    trackNo: bytes.positive(),
    title: id,
  })
  .readonly();
const metadataAlbum = z
  .object({
    albumId: id,
    title: id,
    tracks: z.array(metadataTrack).readonly(),
  })
  .readonly();
const playableShape = {
  playbackItemId: id,
  albumId: id,
  title: id,
  audioAssetId: id,
  durationSeconds: z.number().nonnegative().optional(),
};
const playbackItem = z.discriminatedUnion('kind', [
  z
    .strictObject({
      ...playableShape,
      kind: z.literal('album-crossfade'),
    })
    .readonly(),
  z
    .strictObject({
      ...playableShape,
      kind: z.literal('track'),
      trackId: id,
    })
    .readonly(),
]);

/** 音源の選択IDはcanonicalなAlbum/Track IDと独立。クロスフェードにtrackIdはない。 */
export type PlaybackItem = z.infer<typeof playbackItem>;

const sha256Checksum = z.object({
  algorithm: z.literal('sha256'),
  value: z.string().regex(/^[0-9a-f]{64}$/),
});
const projectionAsset = asset.unwrap().extend({
  byteLength: bytes.positive(),
  checksum: sha256Checksum.readonly(),
});
const projectionPackageShape = {
  ...packageShape,
  assets: z.array(projectionAsset.readonly()).readonly(),
  playbackItems: z.array(playbackItem).readonly(),
};

export const manifestV2Schema = z
  .strictObject({
    ...projectionPackageShape,
    compatibleAppVersion: packageShape.compatibleAppVersion
      .unwrap()
      .strict()
      .readonly(),
    assets: z
      .array(
        projectionAsset
          .extend({
            checksum: sha256Checksum.strict().readonly(),
          })
          .strict()
          .readonly(),
      )
      .readonly(),
    schemaVersion: z.literal(2),
    albums: z
      .array(
        metadataAlbum
          .unwrap()
          .extend({
            artworkAssetId: id.optional(),
            tracks: z
              .array(metadataTrack.unwrap().strict().readonly())
              .readonly(),
          })
          .strict()
          .readonly(),
      )
      .readonly(),
  })
  .refine(
    (m) => compare(m.compatibleAppVersion.minInclusive, [1, 10, 0]) >= 0,
    'schema v2 requires app >= 1.10.0',
  )
  .refine(
    (m) =>
      compare(
        m.compatibleAppVersion.minInclusive,
        m.compatibleAppVersion.maxExclusive,
      ) < 0,
    'empty compatibility range',
  )
  .refine((m) => unique(m.assets.map((a) => a.assetId)), 'duplicate asset id')
  .refine((m) => unique(m.albums.map((a) => a.albumId)), 'duplicate album id')
  .refine(
    (m) => unique(m.albums.flatMap((a) => a.tracks.map((t) => t.trackId))),
    'duplicate track id',
  )
  .refine(
    (m) =>
      m.albums.every(
        (a) => new Set(a.tracks.map((t) => t.trackNo)).size === a.tracks.length,
      ),
    'duplicate track number within album',
  )
  .refine(
    (m) => unique(m.playbackItems.map((p) => p.playbackItemId)),
    'duplicate playback item id',
  )
  .refine(
    (m) =>
      Number.isSafeInteger(m.assets.reduce((sum, a) => sum + a.byteLength, 0)),
    'unsafe package size',
  )
  .refine(
    (m) =>
      m.playbackItems.every((p) =>
        m.albums.some(
          (a) =>
            a.albumId === p.albumId &&
            (p.kind === 'album-crossfade'
              ? true
              : a.tracks.some((t) => t.trackId === p.trackId)),
        ),
      ),
    'playback item requires its album and track',
  )
  .refine(
    (m) =>
      m.playbackItems.every((p) =>
        m.assets.some(
          (a) =>
            a.assetId === p.audioAssetId &&
            a.required &&
            /^audio\/[^\s;]+(?:;.*)?$/.test(a.mediaType),
        ),
      ),
    'playback item requires an audio asset',
  )
  .refine(
    (m) =>
      [
        ...m.presentationAssetIds,
        ...m.albums.flatMap((a) =>
          a.artworkAssetId === undefined ? [] : [a.artworkAssetId],
        ),
      ].every((assetId) => m.assets.some((a) => a.assetId === assetId)),
    'dangling asset reference',
  )
  .readonly();

/** schema v2: 収録曲の説明情報と配布する再生項目を分離したsnapshot。 */
export type InstallationManifestV2 = z.infer<typeof manifestV2Schema>;

const projectionV2Schema = z
  .object({
    ...projectionPackageShape,
    albums: z.array(metadataAlbum).readonly(),
    albumArtworkBindings: z
      .array(z.object({ albumId: id, assetId: id }).readonly())
      .readonly(),
  })
  .refine(
    (input) => unique(input.albumArtworkBindings.map((b) => b.albumId)),
    'duplicate album artwork binding',
  )
  .refine(
    (input) =>
      input.albumArtworkBindings.every((b) =>
        input.albums.some((a) => a.albumId === b.albumId),
      ),
    'unknown album in artwork binding',
  )
  .readonly();

/** 認可済みcanonical snapshotと明示的な再生項目・音源対応。配列順が再生項目の表示順。 */
export type ManifestProjectionV2Input = z.infer<typeof projectionV2Schema>;

/** 入力不備と、生成した配布物の参照・整合性違反を区別する。 */
export type ManifestProjectionV2Result =
  | Readonly<{ kind: 'manifest'; manifest: InstallationManifestV2 }>
  | Readonly<{
      kind: 'invalid-projection' | 'invalid-manifest';
      errors: readonly string[];
    }>;

/** Album選択順と再生項目順を保ち、収録曲をtrackNo順に写す。認可・I/O・音源推測は行わない。 */
export const projectManifestV2 = (
  input: unknown,
): ManifestProjectionV2Result => {
  const parsed = projectionV2Schema.safeParse(input);
  const projected = parsed.success
    ? manifestV2Schema.safeParse({
        packageVersion: parsed.data.packageVersion,
        compatibleAppVersion: parsed.data.compatibleAppVersion,
        assets: parsed.data.assets,
        playbackItems: parsed.data.playbackItems,
        presentationAssetIds: parsed.data.presentationAssetIds,
        schemaVersion: 2,
        albums: parsed.data.albums.map((a) => ({
          ...a,
          tracks: [...a.tracks].sort(
            (left, right) => left.trackNo - right.trackNo,
          ),
          artworkAssetId: parsed.data.albumArtworkBindings.find(
            (b) => b.albumId === a.albumId,
          )?.assetId,
        })),
      })
    : parsed;
  return projected.success
    ? { kind: 'manifest', manifest: projected.data }
    : {
        kind: parsed.success ? 'invalid-manifest' : 'invalid-projection',
        errors: projected.error.issues.map(
          (issue) => `${issue.path.join('.')}: ${issue.message}`,
        ),
      };
};
