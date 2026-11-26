import { execFile } from "node:child_process";
import { resolve } from "node:path";
import { promisify } from "node:util";
import { test } from "@playwright/test";

test("staged production shell works with edge gate and CSP", async () => {
  test.setTimeout(60000);
  await promisify(execFile)(process.execPath, [
    resolve(import.meta.dirname, "../../../infra/release/check-listening-browser.mjs"),
    "http://127.0.0.1:4179",
  ]);
});
