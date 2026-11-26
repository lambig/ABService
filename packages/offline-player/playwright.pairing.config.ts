import { defineConfig } from "@playwright/test";
import original from "./playwright.config";

export default defineConfig({
  ...original,
  testDir: "./flow",
  outputDir: "./test-results/pairing",
  timeout: 60000,
  use: {
    ...original.use,
    baseURL: "https://localhost:4180",
    ignoreHTTPSErrors: true,
    launchOptions: {
      ...original.use?.launchOptions,
      args: [
        ...(original.use?.launchOptions?.args ?? []),
        "--ignore-certificate-errors",
      ],
    },
  },
  webServer: [
    {
      command: "npm run build && npm run preview",
      url: "http://127.0.0.1:4179/offline-player/",
      reuseExistingServer: false,
    },
    {
      command: "npm run build:admin --prefix ../.. && node flow/serve.mjs",
      url: "https://localhost:4180/admin/listening/",
      ignoreHTTPSErrors: true,
      reuseExistingServer: false,
      timeout: 120000,
    },
  ],
});
