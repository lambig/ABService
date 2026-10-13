import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import {randomBytes} from 'node:crypto';
import {fileURLToPath} from 'node:url';

export async function serve(directory) {
  const root = path.resolve(directory), token = randomBytes(24).toString('hex');
  const allowed = new Set(['diagnostic.html', 'diagnostic.mjs', 'diagnostic-clock.mjs', 'index.json']);
  const index = JSON.parse(fs.readFileSync(path.join(root, 'index.json')));
  for (const {id} of index.cases) {
    if (!/^[a-z0-9-]+$/.test(id)) throw Error('Invalid case id');
    allowed.add(id + '.json'); allowed.add(id + '.wav');
  }
  const server = http.createServer((req, res) => {
    const prefix = `/${token}/`;
    const file = req.url?.startsWith(prefix) ? req.url.slice(prefix.length) || 'diagnostic.html' : '';
    if (req.method !== 'GET' || !allowed.has(file)) {res.writeHead(404); res.end(); return;}
    fs.readFile(path.join(root, file), (error, bytes) => {
      if (error) {res.writeHead(404); res.end(); return;}
      res.setHeader('Content-Type', {'.html':'text/html; charset=utf-8','.mjs':'text/javascript','.json':'application/json','.wav':'audio/wav'}[path.extname(file)]);
      res.setHeader('Cache-Control', 'no-store'); res.setHeader('X-Content-Type-Options', 'nosniff');
      res.setHeader('Accept-Ranges', 'bytes');
      if (req.headers.range) {
        const match = /^bytes=(\d+)-(\d*)$/.exec(req.headers.range);
        const start = match ? Number(match[1]) : NaN;
        const end = match?.[2] ? Math.min(Number(match[2]), bytes.length - 1) : bytes.length - 1;
        if (!Number.isSafeInteger(start) || start > end || start >= bytes.length) {
          res.writeHead(416, {'Content-Range':`bytes */${bytes.length}`}); res.end(); return;
        }
        res.writeHead(206, {'Content-Range':`bytes ${start}-${end}/${bytes.length}`, 'Content-Length':end - start + 1});
        res.end(bytes.subarray(start, end + 1)); return;
      }
      res.setHeader('Content-Length', bytes.length);
      res.end(bytes);
    });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return {server, url:`http://127.0.0.1:${server.address().port}/${token}/`};
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const {url} = await serve(process.argv[2] ?? path.join(path.dirname(fileURLToPath(import.meta.url)), 'results', 'diagnostic'));
  console.log(url);
}
