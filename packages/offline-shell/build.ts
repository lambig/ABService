import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import ts from "typescript";
const hash = (input: string): string =>
  createHash("sha256").update(input).digest("hex");
const compile = (input: string): string =>
  ts.transpileModule(input, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.ESNext,
    },
  }).outputText;
const sources = await Promise.all(
  [
    "src/index.html",
    "src/app.ts",
    "src/style.css",
    "worker/sw.ts",
    "build.ts",
  ].map((path) => readFile(path, "utf8")),
);
const [html = "", app = "", css = "", sw = ""] = sources;
const build = async (label: string): Promise<void> => {
  const revision = hash(JSON.stringify([label, ts.version, ...sources]));
  const base = `releases/${revision}/`;
  const files = [
    {
      path: `${base}index.html`,
      content: html
        .replaceAll("__LABEL__", label)
        .replaceAll("__BASE__", `./${base}`),
    },
    {
      path: `${base}app.js`,
      content: `const SHELL_REVISION = ${JSON.stringify(revision)};\n${compile(app)}`,
    },
    { path: `${base}style.css`, content: css },
  ];
  const shell = {
    revision,
    entries: files.map((file) => ({
      path: file.path,
      sha256: hash(file.content),
    })),
  };
  const output = `dist/${label}`;
  await mkdir(`${output}/${base}`, { recursive: true });
  await Promise.all(
    [
      ...files,
      { path: "index.html", content: files[0]?.content ?? "" },
      {
        path: "sw.js",
        content: `const SHELL = ${JSON.stringify(shell)};\n${compile(sw)}`,
      },
      { path: "shell.json", content: JSON.stringify(shell) },
    ].map((file) => writeFile(`${output}/${file.path}`, file.content)),
  );
};
/* Two deterministic deployment fixtures let CI exercise a real worker update. */
await build("v1");
await build("v2");
