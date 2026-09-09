/* eslint-disable functional/immutable-data -- The local test server switches deployment/failure fixtures between serial browser cases. */
import { createServer } from "node:http";
import type { ServerResponse } from "node:http";
import { readFile } from "node:fs/promises";
const manifests = await Promise.all(
  ["v1", "v2"].map(async (label) => ({
    label,
    shell: JSON.parse(await readFile(`dist/${label}/shell.json`, "utf8")) as {
      revision: string;
    },
  })),
);
const state = { version: "v1", fault: "none" };
const send = (
  response: ServerResponse,
  status: number,
  content: string | Buffer,
  type = "text/plain; charset=utf-8",
): void => {
  response.writeHead(status, {
    "Content-Type": type,
    "Cache-Control": "no-store",
  });
  response.end(content);
};
const mime = (path: string): string =>
  path.endsWith(".js")
    ? "text/javascript"
    : path.endsWith(".css")
      ? "text/css"
      : "text/html; charset=utf-8";
const server = createServer((request, response) => {
  const url = new URL(request.url ?? "/", "http://127.0.0.1");
  const control = (): void => {
    state.version = url.searchParams.get("version") === "v2" ? "v2" : "v1";
    state.fault = url.searchParams.get("fault") ?? "none";
    send(response, 200, "ok");
  };
  const serve = async (): Promise<void> => {
    const path = url.pathname.replace(/^\/installation-poc\//, "");
    const release = manifests.find((item) =>
      path.startsWith(`releases/${item.shell.revision}/`),
    );
    const label = release?.label ?? state.version;
    const file = path === "" ? "index.html" : path;
    const allowed = [
      ["index.html", "sw.js"].includes(file),
      /^releases\/[a-f0-9]{64}\/(index.html|app.js|style.css)$/.test(file),
    ].some(Boolean);
    const failing = label === state.version && file.endsWith("/style.css");
    const respond = async (): Promise<void> => {
      const data = await readFile(`dist/${label}/${file}`);
      (failing && state.fault === "http"
        ? () => {
            send(response, 503, "injected download failure");
          }
        : failing && state.fault === "corrupt"
          ? () => {
              send(response, 200, "body { color: red }", "text/css");
            }
          : failing && state.fault === "redirect"
            ? () => {
                response.writeHead(302, { Location: "/other" });
                response.end();
              }
            : () => {
                send(response, 200, data, mime(file));
              })();
    };
    await (
      url.pathname.startsWith("/installation-poc/") && allowed
        ? respond
        : () => {
            send(response, 404, "not found");
            return Promise.resolve();
          }
    )();
  };
  (request.method === "POST" && url.pathname === "/__control"
    ? control
    : () => {
        void serve().catch(() => {
          send(response, 404, "not found");
        });
      })();
});
server.listen(4178, "127.0.0.1");
