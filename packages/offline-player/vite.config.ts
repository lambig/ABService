import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import { distributionFixture } from "./distribution.ts";
export default defineConfig({
  base: "/offline-player/",
  plugins: [distributionFixture()],
  resolve: {
    alias: {
      "player-study": fileURLToPath(new URL("../player/src", import.meta.url)),
    },
  },
  build: { assetsInlineLimit: 0 },
});
