/* eslint-disable functional/immutable-data -- The fixture server holds which version it distributes, switched by the tests. */
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import type { IncomingMessage, ServerResponse } from "node:http";
import type { Plugin } from "vite";
import { manifest as study } from "../player/src/fixture.ts";

/**
 * 検証専用の配布元。backend の試聴配布 API と同じ経路・応答の形を、同梱の合成音源と画像で返す。
 * 本番の配布元ではない。token・署名 URL の形だけを真似し、認可や期限は持たない。
 */
export const fixtureToken = `abs_device_${"e".repeat(64)}`;
export const fixtureSignature = "fixture-signature";
const sha256 = (data: string | Buffer): string =>
  createHash("sha256").update(data).digest("hex");
const artworkBytes = readFileSync(
  new URL("fixtures/artwork.png", import.meta.url),
);
export const artworkAssetId = "artwork-study-01.png";
const audioFiles: Readonly<Record<string, string>> = {
  "tone-first": "first.flac",
  "tone-second": "second.flac",
};
const artwork = {
  assetId: artworkAssetId,
  mediaType: "image/png",
  byteLength: artworkBytes.length,
  checksum: { algorithm: "sha256", value: sha256(artworkBytes) },
  required: true,
};
const albums = study.albums.map((album) => ({
  ...album,
  artistDisplayName: "AB Study",
  ...(album.albumId === "study-01" ? { artworkAssetId } : {}),
}));
/* packageVersion is the digest of the content, as the backend assigns it. */
const versioned = <T extends object>(content: T) => ({
  schemaVersion: 3,
  packageVersion: sha256(JSON.stringify(content)),
  ...content,
});
/**
 * v1: 作品クロスフェードと曲音源の併用。音源 2 つと artwork を必須にする。
 * v2: 作品クロスフェードのみ。v1 と音源 1 つ・artwork を共有し、もう 1 つの音源を参照しなくなる。
 */
export const distributions = {
  v1: versioned({
    compatibleAppVersion: study.compatibleAppVersion,
    presentationAssetIds: [],
    assets: [...study.assets, artwork],
    albums,
    playbackItems: study.playbackItems,
  }),
  v2: versioned({
    compatibleAppVersion: study.compatibleAppVersion,
    presentationAssetIds: [],
    assets: [
      ...study.assets.filter((asset) => asset.assetId === "tone-first"),
      artwork,
    ],
    albums,
    playbackItems: study.playbackItems.filter(
      (item) => item.kind === "album-crossfade",
    ),
  }),
} as const;
type Version = keyof typeof distributions;
const state: { version: Version } = { version: "v1" };

const send = (
  response: ServerResponse,
  status: number,
  body: string | Buffer,
  type: string,
): void => {
  response.writeHead(status, {
    "Content-Type": type,
    "Cache-Control": "no-store",
  });
  response.end(body);
};
const json = (response: ServerResponse, status: number, body: unknown) => {
  send(response, status, JSON.stringify(body), "application/json");
};
const problem = (response: ServerResponse, status: number): void => {
  send(
    response,
    status,
    JSON.stringify({ type: "about:blank", title: "fixture", status }),
    "application/problem+json",
  );
};
const authorized = (request: IncomingMessage): boolean =>
  request.headers.authorization === `Bearer ${fixtureToken}`;
const assetUrl = /^\/api\/v1\/listening\/package\/assets\/([^/]+)\/url$/;
const publicAsset = /^\/assets\/([^/]+)$/;

const handle = (
  request: IncomingMessage,
  response: ServerResponse,
  next: () => void,
): void => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);
  const current = distributions[state.version];
  const audioId = decodeURIComponent(assetUrl.exec(url.pathname)?.[1] ?? "");
  const publicId = decodeURIComponent(publicAsset.exec(url.pathname)?.[1] ?? "");
  const audio = current.playbackItems.some(
    (item) => item.audioAssetId === audioId,
  )
    ? audioFiles[audioId]
    : undefined;
  const routes: readonly (readonly [boolean, () => void])[] = [
    [
      request.method === "POST" && url.pathname === "/__distribution",
      () => {
        const version = url.searchParams.get("version");
        state.version = version === "v2" ? "v2" : "v1";
        response.writeHead(204);
        response.end();
      },
    ],
    [
      url.pathname === "/api/v1/listening/package",
      () => {
        (authorized(request)
          ? () => {
              json(response, 200, current);
            }
          : () => {
              problem(response, 401);
            })();
      },
    ],
    [
      audioId !== "",
      () => {
        (authorized(request)
          ? audio === undefined
            ? () => {
                problem(response, 404);
              }
            : () => {
                json(response, 200, {
                  assetId: audioId,
                  url: `${url.origin}/offline-player/audio/${audio}?X-Amz-Signature=${fixtureSignature}`,
                  expiresAt: new Date(Date.now() + 600_000).toISOString(),
                });
              }
          : () => {
              problem(response, 401);
            })();
      },
    ],
    [
      publicId !== "",
      () => {
        (publicId === artworkAssetId
          ? () => {
              send(response, 200, artworkBytes, "image/png");
            }
          : () => {
              send(response, 404, "not found", "text/plain");
            })();
      },
    ],
  ];
  (routes.find(([matches]) => matches)?.[1] ?? next)();
};

/** vite preview にだけ配布元を足す。build の成果物には含めない。 */
export const distributionFixture = (): Plugin => ({
  name: "listening-distribution-fixture",
  configurePreviewServer: (server) => {
    server.middlewares.use(handle);
  },
});
