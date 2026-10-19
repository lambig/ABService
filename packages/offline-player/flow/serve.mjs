// Local browser fixture only. Generates an ephemeral localhost certificate with OpenSSL.
import { createServer } from "node:https";
import { request } from "node:http";
import { readFile, mkdtemp, rm } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";
import { join, resolve, sep, extname } from "node:path";
import { fileURLToPath } from "node:url";

const directory = await mkdtemp(join(tmpdir(), "abservice-pairing-"));
execFileSync(
  "openssl",
  [
    "req",
    "-x509",
    "-newkey",
    "rsa:2048",
    "-nodes",
    "-days",
    "1",
    "-subj",
    "/CN=localhost",
    "-addext",
    "subjectAltName=DNS:localhost,IP:127.0.0.1",
    "-keyout",
    join(directory, "key.pem"),
    "-out",
    join(directory, "cert.pem"),
  ],
  { stdio: "ignore" },
);
const root = fileURLToPath(
  new URL("../../../frontend-admin/dist/", import.meta.url),
);
const types = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".css": "text/css",
  ".svg": "image/svg+xml",
  ".woff2": "font/woff2",
  ".png": "image/png",
};
const server = createServer(
  {
    key: await readFile(join(directory, "key.pem")),
    cert: await readFile(join(directory, "cert.pem")),
  },
  async (incoming, outgoing) => {
    const path = new URL(incoming.url ?? "/", "https://localhost").pathname;
    if (path.startsWith("/admin/")) {
      const target = resolve(
        root,
        "." + decodeURIComponent(path.slice("/admin".length)),
        path.endsWith("/") ? "index.html" : "",
      );
      try {
        if (!target.startsWith(resolve(root) + sep))
          throw new Error("outside fixture");
        const body = await readFile(target);
        outgoing.writeHead(200, {
          "Content-Type": types[extname(target)] ?? "application/octet-stream",
        });
        outgoing.end(body);
      } catch {
        outgoing.writeHead(404);
        outgoing.end();
      }
      return;
    }
    const upstream = request(
      {
        hostname: "127.0.0.1",
        port: 4179,
        path: incoming.url,
        method: incoming.method,
        headers: { ...incoming.headers, host: "127.0.0.1:4179" },
      },
      (response) => {
        // Only the fixture's synthetic signed URL changes origin. Shell bytes stay unchanged.
        if (response.headers["content-type"]?.includes("application/json")) {
          const chunks = [];
          response.on("data", (chunk) => chunks.push(chunk));
          response.on("end", () => {
            const headers = { ...response.headers };
            delete headers["content-length"];
            outgoing.writeHead(response.statusCode ?? 502, headers);
            outgoing.end(
              Buffer.concat(chunks)
                .toString()
                .replaceAll("http://127.0.0.1:4179", "https://localhost:4180"),
            );
          });
        } else {
          outgoing.writeHead(response.statusCode ?? 502, response.headers);
          response.pipe(outgoing);
        }
      },
    );
    upstream.on("error", () => {
      outgoing.writeHead(502);
      outgoing.end();
    });
    incoming.pipe(upstream);
  },
);
server.listen(4180, "127.0.0.1");
const stop = () => {
  server.close();
  void rm(directory, { recursive: true, force: true }).then(() =>
    process.exit(),
  );
};
process.on("SIGTERM", stop);
process.on("SIGINT", stop);
