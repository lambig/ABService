import { z } from 'zod';

export const id = z.string().trim().min(1);
export const bytes = z
  .number()
  .int()
  .nonnegative()
  .max(Number.MAX_SAFE_INTEGER);
export const version = z.tuple([bytes, bytes, bytes]).readonly();
export const checksum = z.object({ algorithm: id, value: id }).readonly();
export const asset = z
  .object({
    assetId: id,
    mediaType: id,
    byteLength: bytes,
    checksum,
    required: z.boolean(),
  })
  .readonly();
export const packageShape = {
  packageVersion: id,
  compatibleAppVersion: z
    .object({ minInclusive: version, maxExclusive: version })
    .readonly(),
  presentationAssetIds: z.array(id).readonly(),
  assets: z.array(asset).readonly(),
};
export const compare = (
  left: z.infer<typeof version>,
  right: z.infer<typeof version>,
): number =>
  [left[0] - right[0], left[1] - right[1], left[2] - right[2]].find(
    (difference) => difference !== 0,
  ) ?? 0;
export const unique = (values: readonly string[]): boolean =>
  new Set(values).size === values.length;
