// End-to-end smoke test against a running host: one TV, two bots (four for Dreifelder).
// Usage: bun crates/mediagram-game/tools/smoke.mjs [ws://localhost:8080/ws] [--game quiz|poster|race|kopfnuss|dreifelder]
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
  const round = (s.scene?.round ? ` r${s.scene.round}` : "") + (s.kind === "dreifelder" ? ` in=${s.scene.alive.length}` : "");
  const key = s.kind === "lobby" ? `lobby(${m.view.players.length})` : `${s.kind}${round} ${s.scene.stage.stage}`;
  if (log.at(-1) !== key) { log.push(key); console.log("tv:", key); }
});

// Bots cannot see the answer: by default they pick at random, which is enough
// to exercise the flow. `pick` returns a field, or null to leave it unanswered.
const random = (s) => Math.floor(Math.random() * s.options.length);
const bot = (name, delay, pick = random) =>
  client({ t: "hello", role: "phone", name, token: null }, (c, m) => {
    if (m.t !== "phone") return;
    const s = m.view.screen;
    if (s.kind === "choice" && s.picked === null && !c.pending && c.skipped !== s.opens_at) {
      c.pending = true;
      const wait = Math.max(0, s.opens_at - (performance.now() + c.offset)) + delay;
      setTimeout(() => {
        c.pending = false;
        const index = pick(s, c);
        if (index === null) c.skipped = s.opens_at;
        else c.send({ t: "action", action: { t: "choose", index, at: performance.now() + c.offset } });
      }, wait);
    }
  });

// Dreifelder: the dev host has no film library, so every question is from the
// general pack, and bots that read the TV's question can look its answer up.
// The script covers the leaver, the all-wrong round and the 12-question cap.
const pack = game === "dreifelder" ? JSON.parse(await Bun.file(new URL("../questions/allgemein.json", import.meta.url)).text()) : [];
const asked = () => tv.last?.view?.scene?.scene;
const field = (s, right) => {
  const answer = pack.find((q) => q.text === asked()?.stage?.question?.text)?.correct;
  const i = s.options.findIndex((o) => o.label === answer);
  return right ? i : (i + 1) % s.options.length;
};
// Round 3 everyone still in is wrong (nobody goes out); Cem never answers round 1
// and guesses as a ghost after; Dana leaves in round 4; Ana and Ben reach the cap.
const script = {
  Ana: (s) => field(s, asked().round !== 3),
  Ben: (s) => field(s, asked().round !== 3),
  Cem: (s) => (asked().round === 1 ? null : random(s)),
  Dana: (s, c) => (asked().round === 4 ? (c.ws.close(), null) : field(s, asked().round !== 3)),
};
const expected = "Ana=300, Ben=200, Cem=0, Dana=100";

const dreifelder = game === "dreifelder";
const a = bot("Ana", 300, dreifelder ? script.Ana : random);
await sleep(100); // Ana joins first and leads
const b = bot("Ben", 900, dreifelder ? script.Ben : random);
const extra = dreifelder ? [bot("Cem", 600, script.Cem), bot("Dana", 1200, script.Dana)] : [];
await sleep(500);
console.log("phones:", a.last?.view?.screen?.kind, b.last?.view?.screen?.kind, "leader:", a.last?.view?.me?.leader);
b.send({ t: "action", action: { t: "start_game" } }); // not leader → error
await sleep(200);
a.send({ t: "action", action: { t: "configure", game, quiz_mode: "general", kids_only: false } });
await sleep(200);
const settings = tv.last.view.scene.settings;
console.log("tv badge:", settings.game, settings.quiz_mode, "kids_only:", settings.kids_only);
a.send({ t: "action", action: { t: "start_game" } });

// A Kopfnuss game (seven rounds and the climb) runs about five minutes, Dreifelder about two.
const deadline = Date.now() + 420_000;
while (Date.now() < deadline) {
  await sleep(250);
  const s = tv.last?.view?.scene;
  if (s?.kind === game && s.scene.stage.stage === "final") break;
}
const scores = tv.last.view.players.map((p) => `${p.name}=${p.score}`).sort().join(", ");
console.log("final scores:", scores);
if (dreifelder) {
  console.log(scores === expected ? "PASS: finishing order as scripted" : `FAIL: expected ${expected}`);
  process.exitCode = scores === expected ? 0 : 1;
}
console.log("phone A:", JSON.stringify(a.last.view.screen));
a.send({ t: "action", action: { t: "back_to_lobby" } });
await sleep(300);
console.log("after back_to_lobby:", tv.last.view.scene.kind);
for (const c of [tv, a, b, ...extra]) c.ws.close();
