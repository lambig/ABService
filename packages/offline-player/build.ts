import { createHash } from "node:crypto";
import { cp, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import ts from "typescript";
const hash = (input: string): string =>
  createHash("sha256").update(input).digest("hex");
const worker = await readFile("../offline-shell/worker/sw.ts", "utf8");
const assets = await readdir("dist/assets");
const files = await Promise.all(
  ["index.html", ...assets.map((name) => `assets/${name}`)].map(
    async (path) => ({ path, content: await readFile(`dist/${path}`, "utf8") }),
  ),
);
const revision = hash(JSON.stringify([worker, ts.version, ...files]));
const prefix = `releases/${revision}/`;
const output = files.map((file) => ({
  path: `${prefix}${file.path}`,
  content: file.content
    .replaceAll("__SHELL_REVISION__", revision)
    .replaceAll("/offline-player/assets/", `/offline-player/${prefix}assets/`),
}));
await mkdir(`dist/${prefix}assets`, { recursive: true });
await Promise.all(
  output.map((file) => writeFile(`dist/${file.path}`, file.content)),
);
await writeFile("dist/index.html", output[0]?.content ?? "");
const shell = {
  revision,
  entries: output.map((file) => ({
    path: file.path,
    sha256: hash(file.content),
  })),
};
await writeFile(
  "dist/sw.js",
  `const SHELL = ${JSON.stringify(shell)};\n${ts.transpileModule(worker, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText}`,
);
await cp("../player/public/fixtures", "dist/audio", { recursive: true });
