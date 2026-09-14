import { test, expect } from "@playwright/test";
import { createHash, webcrypto } from "node:crypto";
import { readFile } from "node:fs/promises";
import { mock } from "node:test";
import { Script } from "node:vm";
import ts from "typescript";

const content = "verified shell content";
const sha256 = createHash("sha256").update(content).digest("hex");
const revision = "diagnostic-test";
const path = `releases/${revision}/index.html`;
const scope = "http://localhost/installation-poc/";
const cacheName = `abservice-shell:${scope}:${revision}`;

/* Execute the actual worker with native Response/crypto but no browser download. */
const install = async (
  download: (request: Request) => Promise<Response>,
  write: (request: Request, response: Response) => Promise<void> = () =>
    Promise.resolve(),
) => {
  const source = await readFile(
    new URL("../worker/sw.ts", import.meta.url),
    "utf8",
  );
  const put = mock.fn(write);
  const remove = mock.fn(() => Promise.resolve(true));
  const installed = new Promise<void>((resolve, reject) => {
    new Script(
      ts.transpileModule(source, {
        compilerOptions: {
          target: ts.ScriptTarget.ES2022,
          module: ts.ModuleKind.ESNext,
        },
      }).outputText,
    ).runInNewContext({
      SHELL: { revision, entries: [{ path, sha256 }] },
      self: {
        registration: { scope },
        addEventListener: (
          name: string,
          listener: (event: { waitUntil: (task: Promise<void>) => void }) => void,
        ) => {
          (name === "install"
            ? () => {
                listener({
                  waitUntil: (task) => {
                    void task.then(resolve, reject);
                  },
                });
              }
            : () => undefined)();
        },
      },
      caches: {
        open: () => Promise.resolve({ put }),
        delete: remove,
      },
      fetch: download,
      Request,
      URL,
      crypto: webcrypto,
    });
  });
  return { installed, put, remove };
};

test("diagnostics: valid bytes are cached without consuming the response", async () => {
  const download = mock.fn(() => Promise.resolve(new Response(content)));
  const worker = await install(download);
  await worker.installed;
  expect(worker.remove.mock.callCount()).toBe(0);
  expect(worker.put.mock.callCount()).toBe(1);
  const [request, response] = worker.put.mock.calls[0]?.arguments ?? [];
  expect(request?.url).toBe(new URL(path, scope).href);
  expect(request?.cache).toBe("no-store");
  expect(request?.redirect).toBe("error");
  expect(await response?.text()).toBe(content);
});

test("diagnostics: HTTP failure identifies revision and path and rolls back", async () => {
  const worker = await install(() =>
    Promise.resolve(new Response("not found", { status: 404 })),
  );
  await expect(worker.installed).rejects.toThrow(
    `revision=${revision}; path=${path}; HTTP 404`,
  );
  expect(worker.put.mock.callCount()).toBe(0);
  expect(worker.remove.mock.calls[0]?.arguments).toEqual([cacheName]);
});

test("diagnostics: corruption reports both hashes and rolls back", async () => {
  const worker = await install(() =>
    Promise.resolve(new Response("wrong bytes", {
      headers: { "Content-Type": "text/html" },
    })),
  );
  const actual = createHash("sha256").update("wrong bytes").digest("hex");
  await expect(worker.installed).rejects.toThrow(
    `revision=${revision}; path=${path}; SHA-256 mismatch: expected=${sha256}; actual=${actual}; url=; content-type=text/html`,
  );
  expect(worker.put.mock.callCount()).toBe(0);
  expect(worker.remove.mock.calls[0]?.arguments).toEqual([cacheName]);
});

test("diagnostics: fetch rejection identifies the file and rolls back", async () => {
  const worker = await install(() =>
    Promise.reject(new TypeError("network unavailable")),
  );
  await expect(worker.installed).rejects.toThrow(
    `revision=${revision}; path=${path}; fetch failed: TypeError: network unavailable`,
  );
  expect(worker.put.mock.callCount()).toBe(0);
  expect(worker.remove.mock.calls[0]?.arguments).toEqual([cacheName]);
});

test("diagnostics: cache write rejection is distinct from verification failure", async () => {
  const worker = await install(
    () => Promise.resolve(new Response(content)),
    () => Promise.reject(new Error("storage unavailable")),
  );
  await expect(worker.installed).rejects.toThrow(
    `revision=${revision}; path=${path}; cache write failed: Error: storage unavailable`,
  );
  expect(worker.put.mock.callCount()).toBe(1);
  expect(worker.remove.mock.calls[0]?.arguments).toEqual([cacheName]);
});
