type Entry = Readonly<{ path: string; sha256: string }>;
declare const SHELL: Readonly<{ revision: string; entries: readonly Entry[] }>;
const worker = self as unknown as ServiceWorkerGlobalScope;
const prefix = `abservice-shell:${worker.registration.scope}:`;
const cacheName = `${prefix}${SHELL.revision}`;
const urlFor = (entry: Entry): string =>
  new URL(entry.path, worker.registration.scope).href;
const fail = (): never => {
  throw new Error("Shell is incomplete or corrupt");
};
const digest = async (response: Response): Promise<string> =>
  Array.from(
    new Uint8Array(
      await crypto.subtle.digest(
        "SHA-256",
        await response.clone().arrayBuffer(),
      ),
    ),
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");
const verified = async (
  response: Response | undefined,
  entry: Entry,
): Promise<Response> => {
  const value = response ?? fail();
  return value.redirected
    ? fail()
    : value.ok && (await digest(value)) === entry.sha256
      ? value
      : fail();
};
const install = async (): Promise<void> => {
  const cache = await caches.open(cacheName);
  try {
    await SHELL.entries.reduce<Promise<void>>(async (previous, entry) => {
      await previous;
      const request = new Request(urlFor(entry), {
        cache: "no-store",
        credentials: "same-origin",
        redirect: "error",
      });
      const response = await verified(await fetch(request), entry);
      await cache.put(request, response);
    }, Promise.resolve());
  } catch (error) {
    await caches.delete(cacheName);
    throw error;
  }
};
const complete = async (): Promise<boolean> => {
  const cache = await caches.open(cacheName);
  return SHELL.entries
    .reduce<Promise<void>>(async (previous, entry) => {
      await previous;
      await verified(await cache.match(urlFor(entry)), entry);
    }, Promise.resolve())
    .then(
      () => true,
      () => false,
    );
};
worker.addEventListener("install", (event) => {
  event.waitUntil(install());
});
/* No skipWaiting/clients.claim: running pages keep their existing generation until all close. */
/* Old generations are retained: collection must not race a newer installation. */
const selected = (request: Request): Entry | undefined => {
  const url = new URL(request.url);
  const scope = new URL(worker.registration.scope);
  const navigation =
    request.mode === "navigate" &&
    [scope.pathname, `${scope.pathname}index.html`].includes(url.pathname);
  return request.method === "GET" &&
    url.origin === scope.origin &&
    url.search === ""
    ? navigation
      ? SHELL.entries.find((entry) => entry.path.endsWith("/index.html"))
      : SHELL.entries.find((entry) => urlFor(entry) === url.href)
    : undefined;
};
worker.addEventListener("fetch", (event) => {
  const entry = selected(event.request);
  /* An allowlist keeps API/audio/unrelated routes out of the shell cache and navigation fallback. */
  const respond = (item: Entry): void => {
    event.respondWith(
      caches
        .open(cacheName)
        .then(async (cache) => verified(await cache.match(urlFor(item)), item))
        .catch(
          () =>
            new Response(
              "保存済みのアプリが欠落・破損しています。オンラインで準備し直してください。",
              {
                status: 503,
                headers: { "Content-Type": "text/plain; charset=utf-8" },
              },
            ),
        ),
    );
  };
  (entry === undefined
    ? () => undefined
    : () => {
        respond(entry);
      })();
});
worker.addEventListener("message", (event) => {
  const respond = (): void => {
    event.waitUntil(
      complete().then((ready) => {
        event.ports[0]?.postMessage({
          kind: "shell-status",
          revision: SHELL.revision,
          complete: ready,
        });
      }),
    );
  };
  (event.data === "shell-status" ? respond : () => undefined)();
});
