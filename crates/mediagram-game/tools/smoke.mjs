// End-to-end smoke test against a running host: one TV, two bots.
// Usage: bun crates/mediagram-game/tools/smoke.mjs [ws://localhost:8080/ws] [--game quiz|poster|race]
// A race game runs longer than the 5-minute deadline; it still exercises the turn flow.
const args = process.argv.slice(2);
const gi = args.indexOf("--game");
const game = gi >= 0 ? args.splice(gi, 2)[1] : "quiz";
const url = args[0] ?? "ws://localhost:8080/ws";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function client(hello, onMsg) {
  const ws = new WebSocket(url);
  const c = { ws, last: null, offset: 0, send: (m) => ws.send(JSON.stringify(m)) };
  ws.onopen = () => { c.send(hello); c.send({ t: "ping", t0: performance.now() }); };
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.t === "pong") c.offset = m.now + (performance.now() - m.t0) / 2 - performance.now();
    if (m.t === "error") console.log("  error:", m.code, m.message);
    c.last = m;
    onMsg?.(c, m);
  };
  return c;
}

const log = [];
const tv = client({ t: "hello", role: "tv", name: null, token: null }, (_, m) => {
  if (m.t !== "tv") return;
  const s = m.view.scene;
  const round = s.scene?.round ? ` r${s.scene.round}` : "";
  const key = s.kind === "lobby" ? `lobby(${m.view.players.length})` : `${s.kind}${round} ${s.scene.stage.stage}`;
  if (log.at(-1) !== key) { log.push(key); console.log("tv:", key); }
});

// Bot A answers correctly using the TV's knowledge of the answer? It can't see
// it; bots pick fast/slow random answers, which is enough to exercise flow.
const bot = (name, delay) =>
  client({ t: "hello", role: "phone", name, token: null }, (c, m) => {
    if (m.t !== "phone") return;
    const s = m.view.screen;
    if (s.kind === "choice" && s.picked === null && !c.pending) {
      c.pending = true;
      const wait = Math.max(0, s.opens_at - (performance.now() + c.offset)) + delay;
      setTimeout(() => {
        c.send({ t: "action", action: { t: "choose", index: Math.floor(Math.random() * s.options.length), at: performance.now() + c.offset } });
        c.pending = false;
      }, wait);
    }
  });

const a = bot("Ana", 300);
const b = bot("Ben", 900);
await sleep(500);
console.log("phones:", a.last?.view?.screen?.kind, b.last?.view?.screen?.kind, "leader:", a.last?.view?.me?.leader);
b.send({ t: "action", action: { t: "start_game" } }); // not leader → error
await sleep(200);
a.send({ t: "action", action: { t: "configure", game, quiz_mode: "general", kids_only: false } });
await sleep(200);
const settings = tv.last.view.scene.settings;
console.log("tv badge:", settings.game, settings.quiz_mode, "kids_only:", settings.kids_only);
a.send({ t: "action", action: { t: "start_game" } });

const deadline = Date.now() + 300_000;
while (Date.now() < deadline) {
  await sleep(250);
  const s = tv.last?.view?.scene;
  if (s?.kind === game && s.scene.stage.stage === "final") break;
}
console.log("final scores:", tv.last.view.players.map((p) => `${p.name}=${p.score}`).join(", "));
console.log("phone A:", JSON.stringify(a.last.view.screen));
a.send({ t: "action", action: { t: "back_to_lobby" } });
await sleep(300);
console.log("after back_to_lobby:", tv.last.view.scene.kind);
for (const c of [tv, a, b]) c.ws.close();
