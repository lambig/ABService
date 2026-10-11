import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import { defineConfig } from "vite";
import { distributionFixture } from "./distribution.ts";
export default defineConfig({
  base: "/offline-player/",
  plugins: [
    distributionFixture(),
    {
      name: "stage-audio-kernel-notices",
      generateBundle() {
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
