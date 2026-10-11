/* eslint-disable functional/immutable-data -- Fault injection exercises the browser authentication boundary. */
import { expect, test } from "@playwright/test";
import { distribute, prepareAndReload, openManagement } from "./support";

test.beforeEach(async ({ page, request }) => {
  await distribute(request, "v1");
  await prepareAndReload(page);
});

test("短押しで管理は開かず、PIN照合後の操作で再びロックする", async ({
  page,
}) => {
  await page.locator("#management-unlock").click();
  await expect(page.locator("#management-auth")).toBeHidden();
  await expect(page.locator("#management-panel")).toBeHidden();
  await openManagement(page);
  await page.locator("#fullscreen").click();
  await expect(page.locator("#management-panel")).toBeHidden();
  await page.locator("#management-unlock").focus();
  await page.keyboard.down(" ");
  await page.waitForTimeout(2100);
  await page.keyboard.up(" ");
  await page.locator("#management-pin").fill("000000");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#management-auth-error")).toHaveText(
    "PINが違います。",
  );
  await expect(page.locator("#management-panel")).toBeHidden();
  await page.locator("#management-auth-cancel").click();
  await page.reload();
  await expect(page.locator("#management-panel")).toBeHidden();
  await openManagement(page);
  await expect(page.locator(".brand")).not.toHaveAttribute("href");
});

test("初回はPINの設定完了まで展示を開始せず、保存失敗時もロックを保つ", async ({
  page,
}) => {
  await page.evaluate(() => {
    localStorage.removeItem("exhibition-management-v1");
  });
  await page
    .getByRole("button", { name: "Album crossfade", exact: true })
    .click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.locator("#play").click();
  await expect(page.locator("#management-auth-title")).toHaveText(
    "展示の管理PINを設定",
  );
  await page.locator("#management-pin").fill("654321");
  await page.locator("#management-confirm").fill("654322");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#management-auth-error")).toHaveText(
    "PINが一致しません。",
  );
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.evaluate(() => {
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Preserve the native storage receiver. */
    const set = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      (key === "exhibition-management-v1"
        ? () => {
            throw new Error("Storage unavailable");
          }
        : () => {
            set.call(this, key, value);
          })();
    };
  });
  await page.locator("#management-pin").fill("654321");
  await page.locator("#management-confirm").fill("654321");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#management-auth-error")).toHaveText(
    "Storage unavailable",
  );
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.reload();
  await page
    .getByRole("button", { name: "Album crossfade", exact: true })
    .click();
  await expect(page.locator("#play")).toBeEnabled();
  await page.locator("#play").click();
  await page.locator("#management-pin").fill("654321");
  await page.locator("#management-confirm").fill("654321");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect(page.locator("#management-panel")).toBeHidden();
});

test("照合中に戻った場合は遅れて解錠せず、失敗回数制限は再読込後も維持する", async ({
  page,
}) => {
  await page.evaluate(() => {
    const derive = crypto.subtle.deriveBits.bind(crypto.subtle);
    crypto.subtle.deriveBits = async (...args) => {
      await new Promise((resolve) => setTimeout(resolve, 800));
      return derive(...args);
    };
  });
  await page.locator("#management-unlock").focus();
  await page.keyboard.down(" ");
  await page.waitForTimeout(2100);
  await page.keyboard.up(" ");
  await page.locator("#management-pin").fill("123456");
  await page.locator("#management-auth button[type=submit]").click();
  await page.locator("#management-auth-cancel").click();
  await page.waitForTimeout(1000);
  await expect(page.locator("#management-panel")).toBeHidden();
  await page.evaluate(() => {
    const key = "exhibition-management-v1";
    const lock = JSON.parse(localStorage.getItem(key) ?? "{}") as Record<
      string,
      unknown
    >;
    localStorage.setItem(key, JSON.stringify({ ...lock, failures: 4 }));
  });
  await page.reload();
  await page.locator("#management-unlock").focus();
  await page.keyboard.down(" ");
  await page.waitForTimeout(2100);
  await page.keyboard.up(" ");
  await page.locator("#management-pin").fill("000000");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#management-auth-error")).toHaveText(
    "PINが違います。",
  );
  await page.reload();
  await page.locator("#management-unlock").focus();
  await page.keyboard.down(" ");
  await page.waitForTimeout(2100);
  await page.keyboard.up(" ");
  await page.locator("#management-pin").fill("123456");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#management-auth-error")).toContainText(
    "試行回数が多い",
  );
  await expect(page.locator("#management-panel")).toBeHidden();
});
