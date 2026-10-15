/* eslint-disable functional/immutable-data -- Fault injection exercises the browser authentication boundary. */
import { expect, test } from "@playwright/test";
import {
  distribute,
  prepareAndReload,
  openManagement,
  enterManagementPin,
} from "./support";

test.beforeEach(async ({ page, request }) => {
  await distribute(request, "v1");
  await prepareAndReload(page);
});

test("数字キーはPINを入力欄やDOMへ出さず、桁数制限と取消しを維持する", async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 780 });
  await page.locator("#management-unlock").focus();
  await page.keyboard.down(" ");
  await page.waitForTimeout(2100);
  await page.keyboard.up(" ");
  const dialog = page.locator("#management-auth");
  await dialog.screenshot({ path: testInfo.outputPath("pin-keypad.png") });
  await expect(
    dialog.locator("input, textarea, [contenteditable=true]"),
  ).toHaveCount(0);
  for (const digit of "12345")
    await dialog.locator(`[data-digit="${digit}"]`).click();
  await dialog.getByRole("button", { name: "確認", exact: true }).click();
  await expect(page.locator("#management-auth-error")).toContainText("6〜32桁");
  await enterManagementPin(page, "123456");
  await dialog.getByRole("button", { name: "1桁戻す", exact: true }).click();
  await expect(page.locator("#management-pin")).toHaveAttribute(
    "aria-label",
    "管理PIN、5桁入力済み",
  );
  await dialog.locator('[data-digit="6"]').click();
  await expect(dialog).not.toContainText("123456");
  expect(await dialog.innerHTML()).not.toContain("123456");
  await dialog.getByRole("button", { name: "確認", exact: true }).click();
  await expect(page.locator("#management-panel")).toBeVisible();
  await page.reload();
  await page.locator("#management-unlock").focus();
  await page.keyboard.down(" ");
  await page.waitForTimeout(2100);
  await page.keyboard.up(" ");
  await enterManagementPin(page, "7".repeat(40));
  await expect(page.locator("#management-pin")).toHaveAttribute(
    "aria-label",
    "管理PIN、32桁入力済み",
  );
  await dialog.getByRole("button", { name: "消去", exact: true }).click();
  await expect(page.locator("#management-pin")).toHaveAttribute(
    "aria-label",
    "管理PIN、0桁入力済み",
  );
  await enterManagementPin(page, "123456");
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(page.locator("#management-pin")).toHaveAttribute(
    "aria-label",
    "管理PIN、0桁入力済み",
  );
  await expect(page.locator("#management-panel")).toBeHidden();
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
  await enterManagementPin(page, "000000", "pin");
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
  const display = "試験スペース <img src=x onerror=alert(1)>";
  await page.getByLabel("スペース表示", { exact: true }).fill(display);
  await page.getByRole("button", { name: "表示を保存" }).click();
  await expect(page.locator("#management-panel")).toBeHidden();
  await page.reload();
  await expect(page.locator("#space-display")).toHaveText(display);
  await expect(page.locator("#space-display img")).toHaveCount(0);
  await page.locator("#space-form").evaluate((form) => {
    const input = document.querySelector<HTMLInputElement>("#space-name");
    (input === null
      ? () => undefined
      : () => {
          input.value = "locked edit";
        })();
    form.dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );
  });
  await expect(page.locator("#space-display")).toHaveText(display);
  await openManagement(page);
  await page.getByLabel("スペース表示", { exact: true }).fill("");
  await page.getByRole("button", { name: "表示を保存" }).click();
  await expect(page.locator("#space-display")).toHaveText("LISTENING ROOM");
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
  await enterManagementPin(page, "654321", "pin");
  await enterManagementPin(page, "654322", "confirm");
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
  await enterManagementPin(page, "654321", "pin");
  await enterManagementPin(page, "654321", "confirm");
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
  await enterManagementPin(page, "654321", "pin");
  await enterManagementPin(page, "654321", "confirm");
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
  await enterManagementPin(page, "123456", "pin");
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
  await enterManagementPin(page, "000000", "pin");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#management-auth-error")).toHaveText(
    "PINが違います。",
  );
  await page.reload();
  await page.locator("#management-unlock").focus();
  await page.keyboard.down(" ");
  await page.waitForTimeout(2100);
  await page.keyboard.up(" ");
  await enterManagementPin(page, "123456", "pin");
  await page.locator("#management-auth button[type=submit]").click();
  await expect(page.locator("#management-auth-error")).toContainText(
    "試行回数が多い",
  );
  await expect(page.locator("#management-panel")).toBeHidden();
});
