import { createHash } from "node:crypto";
import { cp, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { dirname, relative, resolve, sep } from "node:path";
import ts from "typescript";

const hash = (input: string | Uint8Array): string =>
  createHash("sha256").update(input).digest("hex");
const worker = await readFile("../offline-shell/worker/sw.ts", "utf8");
const assets = await readdir("dist/assets", {
  recursive: true,
  withFileTypes: true,
});
const paths = [
  "index.html",
  ...assets
    .filter((entry) => entry.isFile())
    .map((entry) =>
      relative("dist", resolve(entry.parentPath, entry.name))
        .split(sep)
        .join("/"),
    )
    .sort(),
];
const files = await Promise.all(
  paths.map(async (path) => ({
    path,
    content: await readFile(`dist/${path}`),
  })),
);
const revision = hash(
  JSON.stringify([
    worker,
    ts.version,
    ...files.map((file) => [file.path, hash(file.content)]),
  ]),
);
const prefix = `releases/${revision}/`;
const output = files.map((file) => ({
  path: `${prefix}${file.path}`,
  content: /\.(?:html|css|m?js)$/.test(file.path)
    ? Buffer.from(
        file.content
          .toString("utf8")
          .replaceAll("__SHELL_REVISION__", revision)
          .replaceAll(
            "/offline-player/assets/",
            `/offline-player/${prefix}assets/`,
          ),
      )
    : file.content,
}));
await Promise.all(
  output.map(async (file) => {
    await mkdir(dirname(`dist/${file.path}`), { recursive: true });
    await writeFile(`dist/${file.path}`, file.content);
  }),
);
await cp(`dist/${prefix}index.html`, "dist/index.html");
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
