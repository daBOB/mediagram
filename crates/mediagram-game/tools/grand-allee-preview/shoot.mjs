// Screenshots of the Grand Allee board through the stand-in host: headless Chrome at 960 x 540, scale 2 (1920 x 1080, as the
// TV), one JPEG per preset once the board is up and the camera has settled; prints each frame's draw calls and triangles.
// Usage: bun crates/mediagram-game/tools/grand-allee-preview/shoot.mjs <out-dir> [preset ...]   (needs google-chrome-stable)
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const here = (p) => new URL(p, import.meta.url).pathname;
const [out, ...asked] = process.argv.slice(2);
if (!out) {
  console.error("usage: shoot.mjs <out-dir> [preset ...]");
  process.exit(2);
}
const presets = asked.length ? asked : ["opening", "midgame", "dice", "auction", "entrance", "north"];
mkdirSync(out, { recursive: true });

const found = (stream, re) =>
  new Promise((ok, fail) => {
    let seen = "";
    stream.on("data", (d) => {
      seen += d;
      const m = seen.match(re);
      if (m) ok(m[0]);
    });
    stream.on("end", () => fail(new Error(`nothing like ${re} in: ${seen}`)));
  });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const server = spawn("bun", [here("preview.mjs")], { stdio: ["ignore", "pipe", "inherit"] });
const profile = mkdtempSync(join(tmpdir(), "grand-allee-shoot-"));
const chrome = spawn("google-chrome-stable", [
  "--headless=new", "--remote-debugging-port=0", `--user-data-dir=${profile}`, "--window-size=960,540", "--force-device-scale-factor=2",
  "--hide-scrollbars", "--mute-audio", "--no-first-run", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist", "about:blank",
], { stdio: ["ignore", "ignore", "pipe"] });

try {
  const [base, wsUrl] = await Promise.all([found(server.stdout, /http:\/\/127\.0\.0\.1:\d+\//), found(chrome.stderr, /ws:\/\/\S+/)]);
  const ws = new WebSocket(wsUrl);
  await new Promise((ok, fail) => Object.assign(ws, { onopen: ok, onerror: fail }));
  let id = 0;
  const pending = new Map();
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data), p = pending.get(m.id);
    if (!p) return;
    pending.delete(m.id);
    if (m.error) p.fail(new Error(JSON.stringify(m.error)));
    else p.ok(m.result);
  };
  const call = (method, params = {}, sessionId) =>
    new Promise((ok, fail) => {
      pending.set(++id, { ok, fail });
      ws.send(JSON.stringify({ id, method, params, sessionId }));
    });
  const page = (expression, sessionId) => call("Runtime.evaluate", { expression, returnByValue: true }, sessionId).then((r) => r.result.value);

  for (const name of presets) {
    const { targetId } = await call("Target.createTarget", { url: "about:blank" });
    const { sessionId } = await call("Target.attachToTarget", { targetId, flatten: true });
    await call("Emulation.setDeviceMetricsOverride", { width: 960, height: 540, deviceScaleFactor: 2, mobile: false }, sessionId);
    await call("Page.navigate", { url: `${base}#${name}` }, sessionId);
    const t0 = Date.now();
    let state = {};
    while (Date.now() - t0 < 180_000 && !state.settled) {
      await sleep(250);
      state = JSON.parse(await page("JSON.stringify({ settled: !!window.settled, failed: window.failed || '' })", sessionId));
      if (state.failed) throw new Error(`${name}: ${state.failed}`);
    }
    if (!state.settled) throw new Error(`${name}: the board did not settle in 3 minutes`);
    const { data } = await call("Page.captureScreenshot", { format: "jpeg", quality: 88 }, sessionId);
    const path = join(out, `grand-allee-${name}.jpg`);
    writeFileSync(path, Buffer.from(data, "base64"));
    console.log(path, await page("JSON.stringify(window.info())", sessionId), `${((Date.now() - t0) / 1000).toFixed(1)} s`);
    await call("Target.closeTarget", { targetId });
  }
  ws.close();
} finally {
  chrome.kill();
  server.kill();
  rmSync(profile, { recursive: true, force: true });
}
