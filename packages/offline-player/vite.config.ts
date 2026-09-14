import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
export default defineConfig({
  base: "/offline-player/",
  resolve: {
    alias: {
      "player-study": fileURLToPath(new URL("../player/src", import.meta.url)),
    },
  },
  build: { assetsInlineLimit: 0 },
});
