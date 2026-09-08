import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  outputDir: "./test-results",
  workers: 1,
  retries: 0,
  timeout: 30000,
  expect: { timeout: 10000 },
  use: {
    channel: "chromium",
    baseURL: "http://127.0.0.1:4175",
    viewport: { width: 960, height: 720 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    launchOptions: {
      // TEST-ONLY: GPUを持たないCIでも実WebGPUを実行し、端末性能とは区別する。
      args: [
        "--enable-unsafe-webgpu",
        "--use-webgpu-adapter=swiftshader",
        "--use-angle=swiftshader",
        "--enable-unsafe-swiftshader",
      ],
    },
  },
  webServer: {
    command: "npm run build && npm run preview -- --port 4175 --strictPort",
    url: "http://127.0.0.1:4175",
    reuseExistingServer: false,
  },
});
