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
const manifestSchema = z
  .object({
    schemaVersion: z.literal(1),
    packageVersion: id,
    compatibleAppVersion: z
      .object({ minInclusive: version, maxExclusive: version })
      .readonly(),
    albums: z.array(album).readonly(),
    presentationAssetIds: z.array(id).readonly(),
    assets: z.array(asset).readonly(),
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
