/* eslint-disable functional/immutable-data -- The synthetic authorization fixture changes state after a simulated PC decision. */
import { expect, test } from "@playwright/test";
import { fixtureToken } from "../distribution";
import { entry, restart, staged } from "./support";

const id = "0192f8a0-0000-7000-8000-000000000001";
const base = {
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

test("接続APIの応答から保存し、認証を永続保存せずオフラインで開き直す", async ({
  page,
  context,
}) => {
  const observed: { approved: boolean; polls: number } = {
    approved: false,
    polls: 0,
  };
  await page.route("**/api/v1/listening/authorizations", async (route) => {
    expect(route.request().headers()["authorization"]).toBeUndefined();
    await route.fulfill({
      json: { ...base, code: "ABCD-2345", proof: "a".repeat(64) },
    });
  });
  await page.route(
    "**/api/v1/listening/authorizations/token",
    async (route) => {
      expect(route.request().postDataJSON()).toEqual({
        requestId: id,
        proof: "a".repeat(64),
      });
      observed.polls += 1;
      await route.fulfill({
        json: observed.approved
          ? { ...base, state: "ISSUED", token: fixtureToken, deviceId: id }
          : base,
      });
    },
  );
  await page.goto(entry);
  await page.getByRole("button", { name: "管理画面と接続" }).click();
  await expect(page.getByLabel("接続コード")).toHaveText("ABCD-2345");
  await expect.poll(() => observed.polls).toBe(1);
  await expect(page.locator("#token")).toHaveValue("");
  observed.approved = true;
  await expect(page.locator("#pair-status")).toContainText(
    "接続が許可されました",
  );
  await page.getByRole("button", { name: "アプリと音源を保存" }).click();
  await expect(page.locator("#readiness")).toHaveText(staged);
  expect(
    await page.evaluate(() =>
      JSON.stringify([
        Object.entries(localStorage),
        Object.entries(sessionStorage),
      ]),
    ),
  ).not.toContain(fixtureToken);
  const next = await restart(page, context);
  await expect(
    next.getByRole("button", { name: "Reel study", exact: true }),
  ).toBeVisible();
  await next.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(next.locator("#play-status")).toHaveText(/再生できます|再生中/);
  await expect(next.locator("#token")).toHaveValue("");
});

test("応答喪失で自動再発行せず管理画面での失効を案内する", async ({ page }) => {
  const observed = { starts: 0, polls: 0 };
  await page.route("**/api/v1/listening/authorizations", async (route) => {
    observed.starts += 1;
    await route.fulfill({
      json: { ...base, code: "ABCD-2345", proof: "a".repeat(64) },
    });
  });
  await page.route(
    "**/api/v1/listening/authorizations/token",
    async (route) => {
      observed.polls += 1;
      await route.abort();
    },
  );
  await page.goto(entry);
  await page.getByRole("button", { name: "管理画面と接続" }).click();
  await expect(page.locator("#pair-status")).toContainText(
    "自動では再発行しません",
  );
  expect(observed).toEqual({ starts: 1, polls: 1 });
  await expect(page.locator("#token")).toHaveValue("");
});
