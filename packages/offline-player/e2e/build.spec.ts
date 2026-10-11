import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { promisify } from "node:util";
import { expect, test } from "@playwright/test";

type Source = Readonly<{ path: string; content: string | Uint8Array }>;
type Shell = Readonly<{
  revision: string;
  entries: readonly Readonly<{ path: string; sha256: string }>[];
}>;
const run = promisify(execFile);
const script = resolve(import.meta.dirname, "../build.ts");
const digest = (bytes: Uint8Array): string =>
  createHash("sha256").update(bytes).digest("hex");
const sources: readonly Source[] = [
  {
    path: "assets/audio-kernel-fixture.wasm",
    content: Buffer.from([0, 97, 115, 109, 1, 0, 0, 0]),
  },
  {
    path: "assets/audio-kernel-NOTICES.txt",
    content: "Fixture kernel notices\n",
  },
  {
    path: "index.html",
    content:
      '<html lang="ja"><link rel="stylesheet" href="/offline-player/assets/app.css"><script src="/offline-player/assets/app.js"></script></html>',
  },
  {
    path: "assets/app.css",
    content:
      '@font-face{font-family:JP;src:url("/offline-player/assets/fonts/jp.woff2")}body{font-family:JP}',
  },
  {
    path: "assets/app.js",
    content: 'const revision = "__SHELL_REVISION__";',
  },
  {
    path: "assets/fonts/jp.woff2",
    content: Buffer.concat([
      Buffer.from([0x77, 0x4f, 0x46, 0x32, 0, 0xff, 0x80, 0xc0]),
      Buffer.from("__SHELL_REVISION__/offline-player/assets/"),
    ]),
  },
];
const build = async (
  inputs: readonly Source[],
  check: (
    shell: Shell,
    read: (path: string) => Promise<Buffer>,
  ) => Promise<void> | void,
): Promise<void> => {
  const directory = await mkdtemp(join(tmpdir(), "offline-player-build-"));
  const cwd = join(directory, "offline-player");
  await (async () => {
    await Promise.all(
      [
        ...inputs.map((source) => ({
          ...source,
          path: join("offline-player/dist", source.path),
        })),
        { path: "offline-shell/worker/sw.ts", content: "export {};" },
        { path: "player/public/fixtures/tone.flac", content: "fLaC" },
      ].map(async (source) => {
        const path = join(directory, source.path);
        await mkdir(dirname(path), { recursive: true });
        await writeFile(path, source.content);
      }),
    );
    await run(process.execPath, [script], { cwd });
    const worker = await readFile(join(cwd, "dist/sw.js"), "utf8");
    const declaration = worker.split("\n")[0] ?? "";
    expect(declaration).toMatch(/^const SHELL = .*;$/);
    const shell = JSON.parse(
      declaration.slice("const SHELL = ".length, -1),
    ) as Shell;
    await check(shell, (path) => readFile(join(cwd, "dist", path)));
  })().finally(() => rm(directory, { recursive: true, force: true }));
};

test("shellのバイナリを保持し、参照とハッシュを配布世代に揃える", async () => {
  await build(sources, async (shell, read) => {
    expect(shell.entries).toHaveLength(sources.length);
    await Promise.all(
      shell.entries.map(async (entry) => {
        expect(digest(await read(entry.path))).toBe(entry.sha256);
      }),
    );
    const prefix = `releases/${shell.revision}/`;
    const font = sources.find((source) => source.path.endsWith(".woff2"));
    expect(await read(`${prefix}assets/fonts/jp.woff2`)).toEqual(font?.content);
    expect((await read(`${prefix}assets/app.css`)).toString("utf8")).toContain(
      `/offline-player/${prefix}assets/fonts/jp.woff2`,
    );
    expect((await read(`${prefix}assets/app.js`)).toString("utf8")).toContain(
      shell.revision,
    );
    expect(await read("index.html")).toEqual(await read(`${prefix}index.html`));
    expect((await read("index.html")).toString("utf8")).toContain(
      `/offline-player/${prefix}assets/app.css`,
    );
    expect(shell.entries.some((entry) => entry.path.includes("/audio/"))).toBe(
      false,
    );
    expect((await read("audio/tone.flac")).toString("utf8")).toBe("fLaC");
  });
});

test("作成順に依存せず同じバイト列から同じshell世代を作る", async () => {
  await build(sources, async (first) => {
    await build(
      sources.reduce<readonly Source[]>((items, item) => [item, ...items], []),
      (second) => {
        expect(second).toEqual(first);
      },
    );
  });
});

for (const path of [
  "assets/audio-kernel-fixture.wasm",
  "assets/audio-kernel-NOTICES.txt",
]) {
  test(`${path}の内容を配布世代と保存hashへ反映する`, async () => {
    const updated = sources.map((source) =>
      source.path === path
        ? { ...source, content: Buffer.from("changed fixture bytes") }
        : source,
    );
    await build(sources, async (first) => {
      await build(updated, async (second, read) => {
        expect(second.revision).not.toBe(first.revision);
        const entry = second.entries.find((file) => file.path.endsWith(path));
        expect(entry).toBeDefined();
        const bytes = await read(entry?.path ?? "missing");
        expect(bytes).toEqual(Buffer.from("changed fixture bytes"));
        expect(entry?.sha256).toBe(digest(bytes));
      });
    });
  });
}

test("NOTICE未ステージングのshellを発行しない", async () => {
  await expect(
    build(
      sources.filter((source) => source.path !== "assets/audio-kernel-NOTICES.txt"),
      () => {},
    ),
  ).rejects.toThrow("audio-kernel-NOTICES.txt");
});

test("UTF-8では区別できないバイナリの違いでもshell世代が変わる", async () => {
  const withByte = (byte: number): readonly Source[] =>
    sources.map((source) =>
      source.path.endsWith(".woff2")
        ? { ...source, content: Buffer.from([byte]) }
        : source,
    );
  expect(Buffer.from([0x80]).toString("utf8")).toBe(
    Buffer.from([0x81]).toString("utf8"),
  );
  await build(withByte(0x80), async (first) => {
    await build(withByte(0x81), (second) => {
      expect(second.revision).not.toBe(first.revision);
    });
  });
});
