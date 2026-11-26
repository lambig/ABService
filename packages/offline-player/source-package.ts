import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync, existsSync } from "node:fs";
import path from "node:path";
import { gzipSync } from "node:zlib";
import { fileURLToPath } from "node:url";

const repository = fileURLToPath(new URL("../../", import.meta.url));
const requireSource = (condition: boolean, message: string): void => {
  (condition
    ? () => undefined
    : () => {
        throw new Error(message);
      })();
};
const tarEntry = (name: string, content: Buffer): Buffer => {
  const header = Buffer.alloc(512);
  const split = name.length > 100 ? name.indexOf("/", name.length - 101) : -1;
  const leaf = split < 0 ? name : name.slice(split + 1),
    prefix = split < 0 ? "" : name.slice(0, split);
  requireSource(
    Buffer.byteLength(leaf) <= 100 && Buffer.byteLength(prefix) <= 155,
    `Source path exceeds tar header: ${name}`,
  );
  const field = (offset: number, size: number, value: string): void => {
    header.write(value, offset, size, "utf8");
  };
  field(0, 100, leaf);
  field(100, 8, "0000644");
  field(108, 8, "0000000");
  field(116, 8, "0000000");
  field(124, 12, content.length.toString(8).padStart(11, "0"));
  field(136, 12, "00000000000");
  field(148, 8, "        ");
  field(156, 1, "0");
  field(257, 6, "ustar\0");
  field(263, 2, "00");
  field(345, 155, prefix);
  const checksum = header.reduce((sum, byte) => sum + byte, 0);
  field(148, 8, checksum.toString(8).padStart(6, "0") + "\0 ");
  return Buffer.concat([
    header,
    content,
    Buffer.alloc((512 - (content.length % 512)) % 512),
  ]);
};

/** Includes tracked public files and the sources/notices of bundled npm runtime modules. */
export const correspondingSource = (moduleIds: readonly string[]): Buffer => {
  const tracked: string[] = existsSync(path.join(repository, ".git"))
    ? execFileSync("git", ["-C", repository, "ls-files", "-z"], {
        encoding: "utf8",
      })
        .split("\0")
        .filter(Boolean)
    : (JSON.parse(
        readFileSync(path.join(repository, "SOURCE-FILES.json"), "utf8"),
      ) as string[]);
  for (const name of tracked)
    requireSource(
      typeof name === "string" &&
        (path.isAbsolute(name) ? false : true) &&
        name.split(/[\\/]/).every((part) => part !== ".."),
      "Invalid source archive path",
    );
  const native = JSON.parse(
    readFileSync(
      path.join(repository, "tools/device-analysis/build.json"),
      "utf8",
    ),
  ) as { sources: Record<string, string> };
  for (const name of Object.keys(native.sources))
    requireSource(
      tracked.includes(`tools/device-analysis/${name}`),
      `Corresponding source is not tracked: ${name}`,
    );
  const dependencies = new Set(
    moduleIds.flatMap((id) => {
      const normalized = id.replaceAll("\\", "/").split("?")[0] ?? "";
      const marker = normalized.lastIndexOf("/node_modules/");
      const suffix = normalized.slice(marker + 14).split("/");
      return marker < 0
        ? []
        : [
            normalized.slice(0, marker + 14) +
              suffix
                .slice(0, suffix[0]?.startsWith("@") === true ? 2 : 1)
                .join("/"),
          ];
    }),
  );
  const npmFiles = Array.from(dependencies).flatMap((directory) =>
    readdirSync(directory, { recursive: true, withFileTypes: true })
      .filter((entry) => entry.isFile())
      .map((entry) =>
        path
          .relative(repository, path.join(entry.parentPath, entry.name))
          .replaceAll("\\", "/"),
      ),
  );
  const files = Array.from(new Set([...tracked, ...npmFiles])).sort();
  return gzipSync(
    Buffer.concat([
      ...files.map((name) =>
        tarEntry(
          `ABService/${name}`,
          readFileSync(path.join(repository, name)),
        ),
      ),
      tarEntry(
        "ABService/SOURCE-FILES.json",
        Buffer.from(JSON.stringify(tracked, null, 2) + "\n"),
      ),
      Buffer.alloc(1024),
    ]),
    { level: 9 },
  );
};
