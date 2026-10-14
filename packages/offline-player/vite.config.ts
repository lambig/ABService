import { fileURLToPath } from "node:url";
import { readFileSync, readdirSync } from "node:fs";
import { createHash } from "node:crypto";
import { defineConfig } from "vite";
import { distributionFixture } from "./distribution.ts";
import { correspondingSource } from "./source-package.ts";
export default defineConfig({
  base: "/offline-player/",
  plugins: [
    distributionFixture(),
    {
      name: "stage-audio-kernel-notices",
      generateBundle() {
        this.emitFile({
          type: "asset",
          fileName: "assets/abservice-source.tgz",
          source: correspondingSource(Array.from(this.getModuleIds())),
        });
        this.emitFile({
          type: "asset",
          fileName: "assets/COPYING-GPL-3.0.txt",
          source: readFileSync(
            new URL(
              "../../tools/device-analysis/harmony/COPYING-GPL-3.0.txt",
              import.meta.url,
            ),
          ),
        });
        this.emitFile({
          type: "asset",
          fileName: "assets/analysis-NOTICES.txt",
          source: readFileSync(
            new URL("../../tools/device-analysis/NOTICES.txt", import.meta.url),
          ),
        });
        const directory = new URL("./analysis/", import.meta.url);
        const files = readdirSync(directory)
          .filter((name) => /\.(mjs|wasm)$/.test(name))
          .sort();
        const digest = (content: string | Uint8Array): string =>
          createHash("sha256").update(content).digest("hex");
        const identities = files.map((name) => {
          const source = readFileSync(new URL(name, directory));
          this.emitFile({
            type: "asset",
            fileName: `assets/analysis/${name}`,
            source,
          });
          return [name, digest(source)];
        });
        const identity = digest(JSON.stringify(identities));
        this.emitFile({
          type: "asset",
          fileName: "assets/analysis/identity.json",
          source: JSON.stringify({
            engines: { melody: identity, harmony: identity },
            analysisVersion: identity,
          }),
        });
        this.emitFile({
          type: "asset",
          fileName: "assets/audio-kernel-NOTICES.txt",
          source: readFileSync(
            new URL(
              "../audio-worklet/src/generated/audio-kernel-NOTICES.txt",
              import.meta.url,
            ),
          ),
        });
      },
    },
  ],
  resolve: {
    alias: {
      "player-study": fileURLToPath(new URL("../player/src", import.meta.url)),
    },
  },
  build: { assetsInlineLimit: 0 },
});
