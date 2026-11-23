// Plays one game of Tiến Lên against a running dev host and shoots the TV table and one phone.
// Ben, Cem and Dana are bots on the socket; Ana is the real phone page in headless Chrome, played by taps on its buttons.
// Usage: bun play-tien-len.mjs <port> <out-dir>
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const [port, out] = process.argv.slice(2);
mkdirSync(out, { recursive: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const RANKS = ["three", "four", "five", "six", "seven", "eight", "nine", "ten", "jack", "queen", "king", "ace", "two"];
const SUITS = ["spades", "clubs", "diamonds", "hearts"];
const val = (c) => RANKS.indexOf(c.rank) * 4 + SUITS.indexOf(c.suit);
const rk = (c) => RANKS.indexOf(c.rank);

/** Places in `hand` (lowest first) to lay, or null to pass. A lead always holds the lowest card. */
function choose(hand, table) {
  const byRank = new Map();
  hand.forEach((c, i) => byRank.set(rk(c), [...(byRank.get(rk(c)) ?? []), i]));
  const runFrom = (r, n) => Array.from({ length: n }, (_, k) => byRank.get(r + k)?.at(-1)).every((i) => i !== undefined);
  if (!table.length) {
    const low = rk(hand[0]);
    let n = 0;
    while (byRank.has(low + n)) n++;
    if (n >= 3) return [0, ...Array.from({ length: n - 1 }, (_, k) => byRank.get(low + k + 1)[0])];
    return byRank.get(low);
  }
  const n = table.length, top = Math.max(...table.map(val));
  if (table.every((c) => rk(c) === rk(table[0]))) {
    for (const places of byRank.values()) if (places.length >= n && val(hand[places[n - 1]]) > top) return places.slice(0, n);
    return null;
  }
  if (new Set(table.map(rk)).size !== n) return null; // a run of pairs: the bots let it go
  for (let r = 0; r + n <= RANKS.length; r++) {
    if (!runFrom(r, n)) continue;
    const places = Array.from({ length: n }, (_, k) => byRank.get(r + k).at(-1));
    if (val(hand[places[n - 1]]) > top) return places;
  }
  return null;
}

// ---- socket players
const tvState = { scene: null, onShow: false };
function client(hello, onMsg) {
  const ws = new WebSocket(`ws://127.0.0.1:${port}/ws`);
  const c = { ws, last: null, send: (m) => ws.send(JSON.stringify(m)) };
  ws.onopen = () => c.send(hello);
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.t === "error") console.log("  error:", hello.name, m.code, m.message);
    c.last = m;
    onMsg?.(c, m);
  };
  return c;
}
const tv = client({ t: "hello", role: "tv", name: null, token: null }, (_, m) => {
  if (m.t === "tv") tvState.scene = m.view.scene;
});
const bot = (name) =>
  client({ t: "hello", role: "phone", name, token: null }, (c, m) => {
    if (m.t === "error" && c.acted) return c.send({ t: "action", action: { t: "cards", button: "pass", hand: [], table: null } });
    if (m.t !== "phone" || m.view.screen.kind !== "cards") return;
    const d = m.view.screen.desk, key = d.prompt + d.hand.length + d.table.length;
    tvState.onShow = d.prompt === "Gleich spielst du aus"; // a play nobody may answer lies on show
    if (!d.buttons.length) return void (c.key = null); // the next ask may look the same as the last
    if (c.key === key) return;
    c.key = key;
    setTimeout(() => {
      const places = d.buttons.some((b) => b.label === "Bombe legen") ? null : choose(d.hand.map((h) => h.card), d.table.map((t) => t.card));
      c.acted = true;
      c.send({ t: "action", action: { t: "cards", button: places ? "play" : "pass", hand: places ?? [], table: null } });
    }, 900);
  });

// ---- Chrome
const profile = mkdtempSync(join(tmpdir(), "tien-len-shoot-"));
const chrome = spawn("google-chrome-stable", [
  "--headless=new", "--remote-debugging-port=0", `--user-data-dir=${profile}`, "--hide-scrollbars", "--mute-audio", "--no-first-run",
  "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist", "about:blank",
], { stdio: ["ignore", "ignore", "pipe"] });
const wsUrl = await new Promise((ok, fail) => {
  let seen = "";
  chrome.stderr.on("data", (d) => { seen += d; const m = seen.match(/ws:\/\/\S+/); if (m) ok(m[0]); });
  chrome.stderr.on("end", () => fail(new Error(seen)));
});
const cdp = new WebSocket(wsUrl);
await new Promise((ok, fail) => Object.assign(cdp, { onopen: ok, onerror: fail }));
let id = 0;
const pending = new Map();
cdp.onmessage = (e) => {
  const m = JSON.parse(e.data), p = pending.get(m.id);
  if (!p) return;
  pending.delete(m.id);
  if (m.error) p.fail(new Error(JSON.stringify(m.error)));
  else p.ok(m.result);
};
const call = (method, params = {}, sessionId) => new Promise((ok, fail) => { pending.set(++id, { ok, fail }); cdp.send(JSON.stringify({ id, method, params, sessionId })); });
async function open(url, width, height, mobile, before) {
  const { targetId } = await call("Target.createTarget", { url: "about:blank" });
  const { sessionId } = await call("Target.attachToTarget", { targetId, flatten: true });
  await call("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 2, mobile }, sessionId);
  await call("Page.enable", {}, sessionId);
  if (before) await call("Page.addScriptToEvaluateOnNewDocument", { source: before }, sessionId);
  await call("Page.navigate", { url }, sessionId);
  return {
    run: (expression) => call("Runtime.evaluate", { expression, returnByValue: true }, sessionId).then((r) => r.result.value),
    shoot: async (name) => {
      const { data } = await call("Page.captureScreenshot", { format: "png" }, sessionId);
      writeFileSync(join(out, `${name}.png`), Buffer.from(data, "base64"));
      console.log("shot:", name);
    },
  };
}

const shot = new Set();
const once = async (page, name) => { if (!shot.has(name)) { shot.add(name); await page.shoot(name); } };

try {
  const ben = bot("Ben");
  await sleep(400); // Ben joins first and leads the room
  const phone = await open(`http://127.0.0.1:${port}/`, 412, 915, true, `localStorage.setItem("spieltisch.name","Ana");localStorage.setItem("spieltisch.token","none");`);
  await sleep(1500);
  bot("Cem");
  await sleep(300);
  bot("Dana");
  const tvPage = await open(`http://127.0.0.1:${port}/tv`, 960, 540, false);
  await sleep(1500);
  ben.send({ t: "action", action: { t: "configure", game: "tien_len", quiz_mode: "general", speed: "normal", length: "normal", kids_only: false } });
  await sleep(1500);
  await once(tvPage, "tv-00-lobby");
  await once(phone, "phone-00-lobby");
  ben.send({ t: "action", action: { t: "start_game" } });
  await sleep(6000); // the table loads and the hands are dealt
  await once(tvPage, "tv-01-dealt");

  // Ana's desk as the page shows it
  const read = () => phone.run(`(() => {
    const names = { Kreuz: "clubs", Pik: "spades", Herz: "hearts", Karo: "diamonds", Zwei: "two", Drei: "three", Vier: "four", "Fünf": "five", Sechs: "six", Sieben: "seven", Acht: "eight", Neun: "nine", Zehn: "ten", Bube: "jack", Dame: "queen", "König": "king", Ass: "ace" };
    const cards = (sel) => [...document.querySelectorAll(sel)].map((b) => { const [s, r] = b.getAttribute("aria-label").split(" "); return { suit: names[s], rank: names[r] }; });
    return { prompt: document.querySelector(".desk-prompt")?.textContent ?? null, info: document.querySelector(".desk-info")?.textContent ?? null,
      hand: cards(".desk-hand .pcard"), table: cards(".desk-table .pcard"), buttons: [...document.querySelectorAll(".desk-btn")].map((b) => b.textContent) };
  })()`);
  const tap = (sel, i) => phone.run(`document.querySelectorAll(${JSON.stringify(sel)})[${i}].click()`);

  let lastKey = "";
  const deadline = Date.now() + 240_000;
  while (Date.now() < deadline) {
    await sleep(300);
    const s = tvState.scene;
    if (!s || s.kind !== "cards") { if (shot.has("tv-01-dealt") && s?.kind !== "cards" && shot.has("tv-09-final")) break; continue; }
    const sc = s.scene;
    if (sc.note) await once(tvPage, `tv-02-${sc.note.toLowerCase().replace("ß", "ss")}-${sc.piles[0]?.cards.length}`);
    if (sc.seats.some((x) => x.marker === "Passt")) await once(tvPage, "tv-03-passt");
    if (tvState.onShow) await once(tvPage, "tv-05-on-show");
    if (sc.seats.some((x) => x.marker === "Platz 1")) await once(tvPage, "tv-04-platz-1");
    if (sc.ranking) { await sleep(600); await once(tvPage, "tv-09-final"); await sleep(9000); await once(tvPage, "tv-10-after"); await once(phone, "phone-09-after"); break; }
    const d = await read();
    if (!d.prompt) continue;
    const key = d.prompt + d.hand.length + d.table.length;
    if (key === lastKey) continue;
    lastKey = key;
    if (!d.buttons.length) { await once(phone, d.info ? "phone-05-waiting-" + (d.info.includes("gepasst") ? "passed" : "done") : "phone-04-waiting"); continue; }
    await sleep(700);
    const leading = !d.table.length;
    await once(phone, leading ? (d.info ? "phone-01-first-lead" : "phone-01-lead") : d.info ? "phone-07-bomb-hint" : `phone-02-answer-${d.table.length}`);
    const places = d.buttons.includes("Bombe legen") ? null : choose(d.hand, d.table);
    if (!places) { await tap(".desk-btn", d.buttons.indexOf("Passen")); continue; }
    for (const i of places) await tap(".desk-hand .pcard", i);
    await sleep(300);
    await once(phone, `phone-03-picked-${places.length}`);
    await tap(".desk-btn", 0);
    await sleep(500);
    const toast = await phone.run(`document.querySelector(".toast")?.textContent ?? null`);
    if (toast) { console.log("  Ana refused:", toast); await once(phone, "phone-06-refused"); await tap(".desk-btn", d.buttons.indexOf("Passen")); }
  }
  console.log("ranking:", JSON.stringify(tvState.scene?.scene?.ranking ?? null), "shots:", [...shot].join(" "));
} finally {
  chrome.kill();
  tv.ws.close();
  await sleep(300);
  rmSync(profile, { recursive: true, force: true });
}
process.exit(0);
