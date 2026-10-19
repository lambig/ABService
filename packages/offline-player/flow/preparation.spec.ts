/* eslint-disable functional/immutable-data -- Synthetic API state and request observations belong to this UI integration fixture. */
import { expect, test } from "@playwright/test";
import { fixtureToken } from "../distribution";
import { enterManagementPin, restart, staged } from "../e2e/support";

/* UI integration uses synthetic API responses. DB locking/authentication are tested separately
in ListeningAuthorizationRestIntegrationTest; this is not deployed-device acceptance. */
test("管理画面の入口から別端末を許可し、別端末だけへ保存してオフライン再生する", async ({
  page: pc,
  browser,
}, testInfo) => {
  const id = "0192f8a0-0000-7000-8000-000000000001";
  const pending = {
    state: "PENDING",
    requestId: id,
    label: "試験端末",
    expiresAt: "2099-01-01T00:00:00Z",
    deviceId: null,
    retryAfterSeconds: 5,
    code: null,
    proof: null,
    token: null,
  };
  const observed = { approved: false, starts: 0, packageReads: 0 };
  const device = await browser.newContext({
    baseURL: "https://localhost:4180",
    ignoreHTTPSErrors: true,
  });
  const m21 = await device.newPage();
  await pc.route("**/api/v1/admin/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    expect(route.request().headers()["authorization"]).toBe(
      path.endsWith("/sessions")
        ? "Bearer synthetic-admin-key"
        : "Bearer abs_session_synthetic",
    );
    const json = path.endsWith("/sessions")
      ? {
          token: "abs_session_synthetic",
          expiresAt: new Date(Date.now() + 1800000).toISOString(),
        }
      : path.endsWith("/listening-devices")
        ? { devices: [] }
        : path.endsWith("/albums")
          ? {
              items: [
                {
                  albumId: "study-01",
                  title: "試験作品",
                  publishedAt: "2026-01-01T00:00:00Z",
                },
              ],
              totalPages: 1,
            }
          : path.endsWith("/crossfade")
            ? {
                albumId: "study-01",
                kind: "album-crossfade",
                audioId: id,
                revision: 1,
              }
            : path.includes("/registrations/")
              ? {
                  audioId: id,
                  state: "CONFIRMED",
                  createdAt: "2026-01-01T00:00:00Z",
                  expiresAt: "2099-01-01T00:00:00Z",
                  metadata: { byteLength: 16, sha256: "a".repeat(64) },
                }
              : path.endsWith("/resolve")
                ? (expect(route.request().postDataJSON()).toEqual({
                    code: "ABCD-2345",
                  }),
                  pending)
                : path.endsWith("/decision")
                  ? (expect(route.request().postDataJSON()).toEqual({
                      approve: true,
                      validDays: 30,
                    }),
                    (observed.approved = true),
                    { ...pending, state: "APPROVED" })
                  : undefined;
    expect(json).toBeDefined();
    await route.fulfill({ json });
  });
  await device.route("**/api/v1/listening/authorizations**", async (route) => {
    expect(route.request().headers()["authorization"]).toBeUndefined();
    const poll = route.request().url().endsWith("/token");
    (poll
      ? () => {
          expect(route.request().postDataJSON()).toEqual({
            requestId: id,
            proof: "a".repeat(64),
          });
        }
      : () => {
          observed.starts += 1;
        })();
    await route.fulfill({
      json: poll
        ? observed.approved
          ? { ...pending, state: "ISSUED", deviceId: id, token: fixtureToken }
          : pending
        : { ...pending, code: "ABCD-2345", proof: "a".repeat(64) },
    });
  });
  device.on("request", (request) => {
    expect(new URL(request.url()).pathname.startsWith("/api/v1/admin/")).toBe(
      false,
    );
    expect(request.headers()["authorization"] ?? "").not.toContain(
      "synthetic-admin",
    );
    observed.packageReads +=
      new URL(request.url()).pathname === "/api/v1/listening/package" ? 1 : 0;
  });
  await pc.goto("/admin/");
  await pc.getByRole("link", { name: "試聴機を準備する", exact: true }).click();
  await pc.getByLabel("管理APIの鍵").fill("synthetic-admin-key");
  await pc.getByRole("button", { name: "開く", exact: true }).click();
  await expect(pc.getByText("試験作品", { exact: true })).toBeVisible();
  await expect(pc.getByText(/配布対象：確定済みFLAC/)).toBeVisible();
  const link = await pc
    .getByRole("link", { name: /この端末で開く/ })
    .getAttribute("href");
  expect(link).toBe("https://localhost:4180/offline-player/");
  await m21.goto(link ?? "");
  await m21.getByRole("button", { name: "管理画面と接続" }).click();
  await expect(m21.getByLabel("接続コード")).toHaveText("ABCD-2345");
  const code = await m21.getByLabel("接続コード").textContent();
  await pc.getByLabel("接続コード").fill(code ?? "");
  await pc.getByRole("button", { name: "コードを照合" }).click();
  await expect(
    pc.getByRole("button", { name: "この試聴機を許可" }),
  ).toBeVisible();
  await pc.screenshot({
    path: testInfo.outputPath("pc-preparation.png"),
    fullPage: true,
  });
  await m21.screenshot({
    path: testInfo.outputPath("device-pairing.png"),
    fullPage: true,
  });
  await pc.getByRole("button", { name: "この試聴機を許可" }).click();
  await expect(m21.locator("#pair-status")).toContainText(
    "接続が許可されました",
  );
  expect(observed.packageReads).toBe(0);
  await m21.getByRole("button", { name: "アプリと音源を保存" }).click();
  await expect(m21.locator("#readiness")).toHaveText(staged);
  expect(observed).toEqual({ approved: true, starts: 1, packageReads: 1 });
  const next = await restart(m21, device);
  await next.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(next.locator("#play-status")).toHaveText(/再生できます|再生中/);
  await next.locator("#play").click();
  await enterManagementPin(next, "123456", "pin");
  await enterManagementPin(next, "123456", "confirm");
  await next
    .getByRole("dialog")
    .getByRole("button", { name: "確認", exact: true })
    .click();
  await expect(next.locator("#play-status")).toHaveText("再生中");
  await expect
    .poll(() => next.locator("#seek").inputValue().then(Number))
    .toBeGreaterThan(0.3);
  await next.screenshot({
    path: testInfo.outputPath("device-offline.png"),
    fullPage: true,
  });
  await expect(next.locator("#token")).toHaveValue("");
  expect(
    await next.evaluate(() =>
      JSON.stringify([
        Object.entries(localStorage),
        Object.entries(sessionStorage),
      ]),
    ),
  ).not.toContain(fixtureToken);
  expect(await pc.evaluate(() => caches.keys())).toEqual([]);
  await device.close();
});
