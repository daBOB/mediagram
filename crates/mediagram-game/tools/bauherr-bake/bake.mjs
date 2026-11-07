// Bakes the Bauherr town: paints every surface (hotels, landscapes, ground, shadows) in headless Chrome and writes the
// result as images plus one geometry file into client/public/bauherr/, so the TV only decodes and uploads them.
// Usage: bun crates/mediagram-game/tools/bauherr-bake/bake.mjs   (needs bun install in client/ and google-chrome-stable)
// The painting code lives in page/; textures (Poly Haven, CC0, see textures/sources.json) in textures/.
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { dirname } from 'node:path';

const here = (p) => new URL(p, import.meta.url).pathname;
const CLIENT = here('../../client/');
const OUT_DIR = here('../../client/public/bauherr/');
const require = createRequire(CLIENT + 'package.json');
const THREE_DIR = dirname(require.resolve('three/package.json')) + '/';
const FONT_DIR = dirname(require.resolve('@fontsource/bricolage-grotesque/package.json')) + '/files/';

// the page imports 'three' and the client's TypeScript layout; Bun resolves three from the client's install
const bundled = await Bun.build({
  entrypoints: [here('page/main.mjs')],
  target: 'browser',
  plugins: [{
    name: 'client-three',
    setup(b) {
      b.onResolve({ filter: /^three(\/.*)?$/ }, ({ path }) => ({
        path: path === 'three' ? `${THREE_DIR}build/three.module.js` : `${THREE_DIR}examples/jsm/${path.replace(/^three\/addons\//, '')}`,
      }));
    },
  }],
});
if (!bundled.success) {
  for (const l of bundled.logs) console.error(String(l));
  process.exit(1);
}
const SCRIPT = await bundled.outputs[0].text();
const PAGE = `<!doctype html><meta charset="utf-8"><style>
@font-face{font-family:"Bricolage Grotesque";font-weight:800;src:url(/font/bricolage-grotesque-latin-800-normal.woff2)}
@font-face{font-family:"Bricolage Grotesque";font-weight:700;src:url(/font/bricolage-grotesque-latin-700-normal.woff2)}
</style><script type="module" src="/page.js"></script>`;

mkdirSync(OUT_DIR, { recursive: true });
const file = async (path) => {
  const f = Bun.file(path);
  return (await f.exists()) ? new Response(f) : new Response('not found', { status: 404 });
};
let finish;
const done = new Promise((r) => (finish = r));
const server = Bun.serve({
  port: 0,
  hostname: '127.0.0.1',
  async fetch(req) {
    const path = new URL(req.url).pathname;
    if (path === '/') return new Response(PAGE, { headers: { 'content-type': 'text/html' } });
    if (path === '/page.js') return new Response(SCRIPT, { headers: { 'content-type': 'text/javascript' } });
    if (path.startsWith('/tex/') && /^[\w.-]+$/.test(path.slice(5))) return file(here('textures/') + path.slice(5));
    if (path.startsWith('/font/') && /^[\w.-]+$/.test(path.slice(6))) return file(FONT_DIR + path.slice(6));
    if (req.method !== 'POST') return new Response('not found', { status: 404 });
    if (path.startsWith('/out/')) {
      const name = path.slice(5), body = await req.arrayBuffer();
      if (!/^[a-z0-9-]+\.(webp|png|bin|json)$/.test(name) || !body.byteLength) return new Response('bad output', { status: 400 });
      await Bun.write(OUT_DIR + name, body);
      console.log(`  ${name} ${(body.byteLength / 1024).toFixed(1)} KB`);
    } else if (path === '/log') console.log(await req.text());
    else if (path === '/done') finish(await req.text());
    return new Response('ok');
  },
});

const profile = mkdtempSync(`${tmpdir()}/bauherr-bake-`);
const chrome = Bun.spawn(['google-chrome-stable', '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--js-flags=--max-old-space-size=4096', `--user-data-dir=${profile}`, `http://127.0.0.1:${server.port}/`], { stdout: 'ignore', stderr: 'ignore' });
const timer = setTimeout(() => finish('timed out after 15 minutes'), 15 * 60_000);
const result = await done;
clearTimeout(timer);
chrome.kill();
await chrome.exited;
server.stop(true);
rmSync(profile, { recursive: true, force: true });
if (result !== 'ok') {
  console.error('bake failed:', result);
  process.exit(1);
}
console.log(`baked into ${OUT_DIR}`);
