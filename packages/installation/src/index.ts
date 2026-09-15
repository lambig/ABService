import { z } from 'zod';

const id = z.string().trim().min(1);
const bytes = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const version = z.tuple([bytes, bytes, bytes]).readonly();
const checksum = z.object({ algorithm: id, value: id }).readonly();
const asset = z
  .object({
    assetId: id,
    mediaType: id,
    byteLength: bytes,
    checksum,
    required: z.boolean(),
  })
  .readonly();
const track = z
  .object({
    trackId: id,
    title: id,
    audioAssetId: id,
    durationSeconds: z.number().nonnegative().optional(),
  })
  .readonly();
const album = z
  .object({
    albumId: id,
    title: id,
    artworkAssetId: id.optional(),
    tracks: z.array(track).readonly(),
  })
  .readonly();

/** 数値3要素の安定版バージョン。pre-release の互換性はこのPoCの対象外。 */
export type AppVersion = z.infer<typeof version>;

const compare = (left: AppVersion, right: AppVersion): number =>
  [left[0] - right[0], left[1] - right[1], left[2] - right[2]].find(
    (difference) => difference !== 0,
  ) ?? 0;
const unique = (values: readonly string[]): boolean =>
  new Set(values).size === values.length;
const packageShape = {
  packageVersion: id,
  compatibleAppVersion: z
    .object({ minInclusive: version, maxExclusive: version })
    .readonly(),
  presentationAssetIds: z.array(id).readonly(),
  assets: z.array(asset).readonly(),
};
const manifestSchema = z
  .object({
    ...packageShape,
    schemaVersion: z.literal(1),
    albums: z.array(album).readonly(),
  })
  .refine(
    (manifest) =>
      compare(
        manifest.compatibleAppVersion.minInclusive,
        manifest.compatibleAppVersion.maxExclusive,
      ) < 0,
    'empty compatibility range',
  )
  .refine(
    (manifest) => unique(manifest.assets.map((item) => item.assetId)),
    'duplicate asset id',
  )
  .refine(
    (manifest) => unique(manifest.albums.map((item) => item.albumId)),
    'duplicate album id',
  )
  .refine(
    (manifest) =>
      unique(
        manifest.albums.flatMap((item) =>
          item.tracks.map((entry) => entry.trackId),
        ),
      ),
    'duplicate track id',
  )
  .refine(
    (manifest) =>
      Number.isSafeInteger(
        manifest.assets.reduce((sum, item) => sum + item.byteLength, 0),
      ),
    'unsafe package size',
  )
  .refine(
    (manifest) =>
      manifest.albums
        .flatMap((item) => item.tracks)
        .every((item) =>
          manifest.assets.some(
            (entry) =>
              entry.assetId === item.audioAssetId &&
              entry.required &&
              entry.mediaType.startsWith('audio/'),
          ),
        ),
    'track requires an audio asset',
  )
  .refine(
    (manifest) =>
      [
        ...manifest.presentationAssetIds,
        ...manifest.albums.flatMap((item) =>
          item.artworkAssetId === undefined ? [] : [item.artworkAssetId],
        ),
      ].every((assetId) =>
        manifest.assets.some((entry) => entry.assetId === assetId),
      ),
    'dangling asset reference',
  )
  .readonly();

/** Album/Trackをcanonical dataとする配布projection。URLや保存キーをidentityにしない。 */
export type InstallationManifest = z.infer<typeof manifestSchema>;

/** schemaVersion未対応と、不正な内容を分ける。成功値は入れ子もreadonlyなsnapshot。 */
export type ManifestResult =
  | Readonly<{ kind: 'manifest'; manifest: InstallationManifest }>
  | Readonly<{ kind: 'unsupported-schema' }>
  | Readonly<{ kind: 'invalid-manifest'; errors: readonly string[] }>;

/** JSONデコード済みの外部入力を検証する。JSON文字列のパースやI/Oは呼び出し元が担う。 */
export const parseManifest = (input: unknown): ManifestResult => {
  const envelope = z
    .object({ schemaVersion: z.number().int() })
    .safeParse(input);
  const parsed = manifestSchema.safeParse(input);
  return envelope.success && envelope.data.schemaVersion !== 1
    ? { kind: 'unsupported-schema' }
    : parsed.success
      ? { kind: 'manifest', manifest: parsed.data }
      : {
          kind: 'invalid-manifest',
          errors: parsed.error.issues.map(
            (issue) => `${issue.path.join('.')}: ${issue.message}`,
          ),
        };
};

const projectionSchema = z
  .object({
    ...packageShape,
    albums: z
      .array(
        z
          .object({
            albumId: id,
            title: id,
            tracks: z
              .array(
                z
                  .object({
                    trackId: id,
                    trackNo: bytes.positive(),
                    title: id,
                  })
                  .readonly(),
              )
              .readonly(),
          })
          .readonly(),
      )
      .readonly(),
    trackAudioBindings: z
      .array(
        z
          .object({
            trackId: id,
            assetId: id,
            durationSeconds: z.number().nonnegative().optional(),
          })
          .readonly(),
      )
      .readonly(),
    albumArtworkBindings: z
      .array(
        z
          .object({
            albumId: id,
            assetId: id,
          })
          .readonly(),
      )
      .readonly(),
  })
  .refine(
    (input) =>
      input.albums.every(
        (item) =>
          new Set(item.tracks.map((entry) => entry.trackNo)).size ===
          item.tracks.length,
      ),
    'duplicate track number within album',
  )
  .refine(
    (input) => unique(input.trackAudioBindings.map((item) => item.trackId)),
    'duplicate track audio binding',
  )
  .refine(
    (input) => unique(input.albumArtworkBindings.map((item) => item.albumId)),
    'duplicate album artwork binding',
  )
  .refine(
    (input) =>
      input.albums
        .flatMap((item) => item.tracks)
        .every((entry) =>
          input.trackAudioBindings.some(
            (binding) => binding.trackId === entry.trackId,
          ),
        ),
    'missing track audio binding',
  )
  .refine(
    (input) =>
      input.trackAudioBindings.every((binding) =>
        input.albums.some((item) =>
          item.tracks.some((entry) => entry.trackId === binding.trackId),
        ),
      ),
    'unknown track in audio binding',
  )
  .refine(
    (input) =>
      input.albumArtworkBindings.every((binding) =>
        input.albums.some((item) => item.albumId === binding.albumId),
      ),
    'unknown album in artwork binding',
  )
  .readonly();

/**
 * v1.0のID付きAlbum/Track snapshotと配布用assetの対応表。
 * 公開APIの曲目はtrackIdを持たないため入力元にできない。
 * 音源・artworkの同定とchecksum計算は呼び出し元が担い、URLから推測しない。
 */
export type ManifestProjectionInput = z.infer<typeof projectionSchema>;

/** 入力・対応表の不備と、生成先Manifestの契約違反を区別する。 */
export type ManifestProjectionResult =
  | Exclude<ManifestResult, { kind: 'unsupported-schema' }>
  | Readonly<{ kind: 'invalid-projection'; errors: readonly string[] }>;

/**
 * Albumの入力順とTrackのtrackNo昇順でschema v1の配布snapshotを生成する純粋関数。
 * 音源の対応漏れを黙って除外せず、入力と完成Manifestの両方を検証する。
 * 公開可否・配布権限の判定や取得処理は行わない。
 */
export const projectManifest = (input: unknown): ManifestProjectionResult => {
  const parsed = projectionSchema.safeParse(input);
  const projected = parsed.success
    ? manifestSchema.safeParse({
        ...parsed.data,
        schemaVersion: 1,
        albums: parsed.data.albums.map((item) => ({
          albumId: item.albumId,
          title: item.title,
          artworkAssetId: parsed.data.albumArtworkBindings.find(
            (binding) => binding.albumId === item.albumId,
          )?.assetId,
          tracks: [...item.tracks]
            .sort((left, right) => left.trackNo - right.trackNo)
            .map((entry) => {
              const binding = parsed.data.trackAudioBindings.find(
                (candidate) => candidate.trackId === entry.trackId,
              );
              return {
                trackId: entry.trackId,
                title: entry.title,
                audioAssetId: binding?.assetId,
                durationSeconds: binding?.durationSeconds,
              };
            }),
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

const environmentSchema = z
  .object({
    appVersion: version,
    appShellAvailable: z.boolean(),
    availableBytes: bytes,
    inventory: z
      .array(z.object({ assetId: id, byteLength: bytes, checksum }).readonly())
      .readonly(),
  })
  .refine(
    (environment) => unique(environment.inventory.map((item) => item.assetId)),
    'duplicate inventory asset',
  )
  .readonly();

/** checksumは期待値のコピーではなく、ローカル実体からadapterが算出した観測値を渡す。 */
export type LocalEnvironment = z.infer<typeof environmentSchema>;

/** 判定可能な準備状態。容量は不足・破損required assetを再取得するための追加容量。 */
export type Readiness = Readonly<{
  kind: 'assessed';
  appCompatible: boolean;
  appShellAvailable: boolean;
  storageCapacitySufficient: boolean;
  requiredAssetsPresent: boolean;
  checksumsValid: boolean;
  packageComplete: boolean;
  offlineReady: boolean;
  requiredDownloadBytes: number;
  missingAssetIds: readonly string[];
  corruptAssetIds: readonly string[];
}>;

/** 入力が不正な場合は未評価として返し、offlineReady=trueへ紛れ込ませない。 */
export type ReadinessResult =
  | Readiness
  | Exclude<ManifestResult, { kind: 'manifest' }>
  | Readonly<{ kind: 'invalid-environment' }>;

const assess = (
  manifest: InstallationManifest,
  environment: LocalEnvironment,
): Readiness => {
  const required = manifest.assets.filter((item) => item.required);
  const missing = required.filter((item) =>
    environment.inventory.every((local) => local.assetId !== item.assetId),
  );
  const corrupt = required.filter((item) =>
    environment.inventory.some(
      (local) =>
        local.assetId === item.assetId &&
        [
          local.byteLength !== item.byteLength,
          local.checksum.algorithm !== item.checksum.algorithm,
          local.checksum.value !== item.checksum.value,
        ].some(Boolean),
    ),
  );
  const requiredDownloadBytes = [...missing, ...corrupt].reduce(
    (sum, item) => sum + item.byteLength,
    0,
  );
  const appCompatible =
    compare(
      environment.appVersion,
      manifest.compatibleAppVersion.minInclusive,
    ) >= 0 &&
    compare(
      environment.appVersion,
      manifest.compatibleAppVersion.maxExclusive,
    ) < 0;
  const packageComplete = missing.length === 0 && corrupt.length === 0;
  const storageCapacitySufficient =
    environment.availableBytes >= requiredDownloadBytes;
  return Object.freeze({
    kind: 'assessed',
    appCompatible,
    appShellAvailable: environment.appShellAvailable,
    storageCapacitySufficient,
    requiredAssetsPresent: missing.length === 0,
    checksumsValid: corrupt.length === 0,
    packageComplete,
    offlineReady:
      packageComplete &&
      appCompatible &&
      environment.appShellAvailable &&
      storageCapacitySufficient,
    requiredDownloadBytes,
    missingAssetIds: Object.freeze(missing.map((item) => item.assetId)),
    corruptAssetIds: Object.freeze(corrupt.map((item) => item.assetId)),
  });
};

/** 純粋なreadiness判定。Manifestの検証とローカルinventoryの検証を必ず通す。 */
export const assessReadiness = (
  input: unknown,
  local: unknown,
): ReadinessResult => {
  const parsed = parseManifest(input);
  const environment = environmentSchema.safeParse(local);
  return parsed.kind !== 'manifest'
    ? parsed
    : environment.success
      ? assess(parsed.manifest, environment.data)
      : { kind: 'invalid-environment' };
};
