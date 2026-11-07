// Serves a stand-in host for the Bauherr board (see preview/entry.ts) and prints its address: open it in a browser to
// look at the baked town, or drive it with window.setScene from a script (headless Chrome at 960 x 540, scale 2, matches the TV).
// Usage: bun crates/mediagram-game/tools/bauherr-bake/preview.mjs [port]   (needs bun install in client/)
import { createRequire } from 'node:module';
import { dirname } from 'node:path';

const here = (p) => new URL(p, import.meta.url).pathname;
const PUBLIC = here('../../client/public/');
const require = createRequire(here('../../client/package.json'));
const FONT_DIR = dirname(require.resolve('@fontsource/bricolage-grotesque/package.json')) + '/files/';

const bundled = await Bun.build({ entrypoints: [here('preview/entry.ts')], target: 'browser' });
if (!bundled.success) {
  for (const l of bundled.logs) console.error(String(l));
  process.exit(1);
}
const SCRIPT = await bundled.outputs[0].text();
const PAGE = `<!doctype html><meta charset="utf-8"><style>
@font-face{font-family:"Bricolage Grotesque";font-weight:800;src:url(/font/bricolage-grotesque-latin-800-normal.woff2)}
@font-face{font-family:"Bricolage Grotesque";font-weight:700;src:url(/font/bricolage-grotesque-latin-700-normal.woff2)}
body{margin:0}.tv-race{position:fixed;inset:0;background:#1a110b}.tv-race-canvas{width:100%;height:100%;display:block}
</style><section class="tv-race" id="stage"></section><script type="module" src="/entry.js"></script>`;
const safe = /^[\w./-]+$/;
const file = async (path) => {
  const f = Bun.file(path);
  return (await f.exists()) ? new Response(f) : new Response('not found', { status: 404 });
};
const server = Bun.serve({
  port: Number(process.argv[2] ?? 0),
  hostname: '127.0.0.1',
  fetch(req) {
    const path = new URL(req.url).pathname;
    if (req.method === 'POST' && path === '/log') return req.text().then((t) => { console.log(t); return new Response('ok'); });
    if (path === '/') return new Response(PAGE, { headers: { 'content-type': 'text/html' } });
    if (path === '/entry.js') return new Response(SCRIPT, { headers: { 'content-type': 'text/javascript' } });
    if (path.startsWith('/font/') && safe.test(path.slice(6))) return file(FONT_DIR + path.slice(6));
    if (safe.test(path.slice(1)) && !path.includes('..')) return file(PUBLIC + path.slice(1));
    return new Response('not found', { status: 404 });
  },
});
console.log(`http://127.0.0.1:${server.port}/`);
