import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  outputDir: "./test-results",
  workers: 1,
  reporter: [["list"], ["json", { outputFile: "test-results/results.json" }]],
  retries: 0,
  timeout: 30000,
  expect: { timeout: 10000 },
  use: {
    baseURL: "http://127.0.0.1:4178",
    viewport: { width: 1100, height: 900 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "npm run build && npm run preview",
    url: "http://127.0.0.1:4178/installation-poc/",
    reuseExistingServer: false,
  },
});
