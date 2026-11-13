// Throwaway: bundles mockup.tsx with the client's packages, serves it with the client's
// avatars, and screenshots every screen with headless Chrome (TV 960x540 at scale 2 = 1080p,
// phones 390x800 at scale 2). Usage: bun shoot.mjs <out-dir> [screen ...]
import { spawnSync } from "node:child_process";
import { mkdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";

const here = (p) => new URL(p, import.meta.url).pathname;
const CLIENT = here("../../../crates/mediagram-game/client/");
const [out, ...asked] = process.argv.slice(2);
mkdirSync(out, { recursive: true });

// bare imports resolve from the client, which owns the packages
const fromClient = {
  name: "from-client",
  setup(b) {
    b.onResolve({ filter: /^[@a-z]/ }, (a) => ({ path: Bun.resolveSync(a.path, CLIENT) }));
  },
};
const bundle = here("bundle/");
rmSync(bundle, { recursive: true, force: true });
console.log("bundling …");
const built = await Bun.build({ entrypoints: [here("mockup.tsx")], outdir: bundle, target: "browser", plugins: [fromClient], naming: { asset: "[name].[ext]" } });
if (!built.success) {
  for (const l of built.logs) console.error(String(l));
  process.exit(1);
}
console.log("bundled", built.outputs.map((o) => o.path.slice(bundle.length)).join(" "));
const css = built.outputs.filter((o) => o.path.endsWith(".css")).map((o) => `<link rel="stylesheet" href="/${o.path.slice(bundle.length)}">`).join("");
const PAGE = `<!doctype html><html lang="de"><meta charset="utf-8">${css}<body><div id="root"></div><script type="module" src="/mockup.js"></script>`;

const server = Bun.serve({
  port: 0,
  hostname: "127.0.0.1",
  async fetch(req) {
    const path = decodeURIComponent(new URL(req.url).pathname);
    if (path === "/") return new Response(PAGE, { headers: { "content-type": "text/html" } });
    if (path.includes("..")) return new Response("no", { status: 400 });
    for (const root of [bundle, join(CLIENT, "public/")]) {
      const f = Bun.file(root + path.slice(1));
      if (await f.exists()) return new Response(f);
    }
    return new Response("not found", { status: 404 });
  },
});

const screens = asked.length ? asked : ["tv-1-who", "tv-2-lines", "tv-3-hidden", "tv-4-play-legend",
  "phone-1-role-fugitive", "phone-4-fugitive-waits", "phone-2-role-hunter-green", "phone-3-hunter-moves-green",
  "phone-2-role-hunter-blue", "phone-3-hunter-moves-blue"];
for (const s of screens) {
  const size = s.startsWith("tv") ? "960,540" : "390,800";
  const png = join(out, `${s}.png`);
  // async: the page is served from this same process
  const chrome = Bun.spawn(["google-chrome-stable", "--headless=new", "--hide-scrollbars", "--no-first-run", `--window-size=${size}`,
    "--force-device-scale-factor=2", "--virtual-time-budget=4000", `--screenshot=${png}`, `http://127.0.0.1:${server.port}/#${s}`], { stdout: "ignore", stderr: "pipe" });
  if ((await chrome.exited) !== 0) console.error(s, await new Response(chrome.stderr).text());
  else console.log(png);
}
server.stop(true);
