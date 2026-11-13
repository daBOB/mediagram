// Throwaway mockup, never shipped: the real Flüchtig TV and phone screens with the
// proposed rules cards, role screens and line legend drawn over them, for a table
// of four where every hunter has one figure. One screen per address hash.
import "@fontsource/bricolage-grotesque/latin-600.css";
import "@fontsource/bricolage-grotesque/latin-800.css";
import "@fontsource/atkinson-hyperlegible/latin-400.css";
import "@fontsource/atkinson-hyperlegible/latin-700.css";
import "../../../crates/mediagram-game/client/src/styles.css";
import "./mockup.css";
import { useLayoutEffect, useRef, type CSSProperties, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import city from "../../../crates/mediagram-game/cities/fluechtig-big-city.json";
import bridged from "../bridges/city-6-bridges.json";
import { riverY } from "../../../crates/mediagram-game/client/src/tv/fluechtig/fluechtig-river";
import type { Connection } from "../../../crates/mediagram-game/client/src/net";
import { PhoneFluechtig } from "../../../crates/mediagram-game/client/src/phone/PhoneFluechtig";
import type { FluechtigPhone } from "../../../crates/mediagram-game/client/src/protocol/FluechtigPhone";
import type { FluechtigScene } from "../../../crates/mediagram-game/client/src/protocol/FluechtigScene";
import type { FluechtigTicket } from "../../../crates/mediagram-game/client/src/protocol/FluechtigTicket";
import type { PlayerInfo } from "../../../crates/mediagram-game/client/src/protocol/PlayerInfo";
import { TvFluechtig } from "../../../crates/mediagram-game/client/src/tv/TvFluechtig";
import { avatarFigureUrl, avatarHeadUrl } from "../../../crates/mediagram-game/client/src/ui/Token";

const conn = { serverNow: () => 0, send: () => {} } as unknown as Connection;
const p = (id: number, name: string, color: string, avatar: string, leader = false): PlayerInfo => ({ id, name, color, score: 0, connected: true, leader, avatar });
const DANA = p(1, "Dana", "#9b5de5", "fox"), BEN = p(2, "Ben", "#ef476f", "bear", true), CEM = p(3, "Cem", "#118ab2", "panda"), ELA = p(4, "Ela", "#06d6a0", "frog");
const players = [DANA, BEN, CEM, ELA];
const HUNTERS = [BEN, CEM, ELA]; // one figure each

// ---- the big city and its moves
type Kind = FluechtigTicket;
type CityFile = typeof city;
const linksOf = (c: CityFile) => (["taxi", "bus", "ubahn"] as const).flatMap((k) => (c[k] as number[][]).map(([a, b]) => ({ a, b, kind: k as Kind })))
  .concat((c.ferry as number[][]).map(([a, b]) => ({ a, b, kind: "schatten" as Kind })));
const boardOf = (c: CityFile) => ({
  width: c.size[0], height: c.size[1], links: linksOf(c),
  spots: c.spots.map(([x, y]) => ({ x, y })), river: c.river.map(([x, y]) => ({ x, y })),
});
const board = boardOf(city);
const links = board.links;
const exits = (f: number, k?: Kind) => links.filter((l) => (l.a === f || l.b === f) && (!k || l.kind === k)).map((l) => (l.a === f ? l.b : l.a));
const ring = (f: number): Kind => (["ubahn", "bus", "schatten"] as Kind[]).find((k) => exits(f, k).length > 0) ?? "taxi";
const T = (taxi: number, bus: number, ubahn: number, schatten = 0, doppel = 0) => ({ taxi, bus, ubahn, schatten, doppel });

// round 1, before the first move: the rules cards play over this
const startAt = [city.hunter_starts[1], city.hunter_starts[5], city.hunter_starts[8]];
const start: FluechtigScene = {
  board, round: 1, rounds: 24, reveal_rounds: [3, 8, 13, 18, 24], next_reveal: 3, fugitive: DANA.id,
  hunters: startAt.map((at, i) => ({ player: HUNTERS[i].id, at, tickets: T(10, 8, 4) })),
  fugitive_tickets: T(4, 3, 3, 5, 2), turn: { turn: "fugitive", double_left: 0 }, targets: [], log: [],
  hideouts: (city.fugitive_starts as number[]).filter((f) => !startAt.includes(f)), news: [], route: null,
};

// round 5, Ben's turn: Dana was seen on 66 in round 3, then rode taxi and bus
const at5 = [106, 166, 27];
const after = (from: number[], k: Kind) => [...new Set(from.flatMap((f) => exits(f, k)))].filter((f) => !at5.includes(f));
const hide5 = after(after([66], "taxi"), "bus").sort((a, b) => a - b);
const benMoves = (["taxi", "bus", "ubahn"] as Kind[]).flatMap((k) => exits(106, k).filter((f) => !at5.includes(f)).map((to) => ({ to, k })));
const play: FluechtigScene = {
  ...start, round: 5, next_reveal: 8,
  hunters: at5.map((at, i) => ({ player: HUNTERS[i].id, at, tickets: T(8, 7, 4) })),
  fugitive_tickets: T(3, 4, 4, 5, 2), turn: { turn: "hunter", index: 0 },
  targets: [...new Set(benMoves.map((m) => m.to))],
  log: [
    { round: 1, tickets: ["taxi"], seen_at: null }, { round: 2, tickets: ["bus"], seen_at: null },
    { round: 3, tickets: ["taxi"], seen_at: 66 }, { round: 4, tickets: ["taxi"], seen_at: null }, { round: 5, tickets: ["bus"], seen_at: null },
  ],
  hideouts: hide5, news: ["Ela fährt mit dem Taxi nach 27."],
};

const phoneBase = {
  round: 5, rounds: 24, next_reveal: 8, fugitive: "Dana", seen: { field: 66, round: 3 }, hideouts: hide5,
  fugitive_tickets: play.fugitive_tickets, log: play.log,
};
const benPhone: FluechtigPhone = {
  ...phoneBase, hiding: null,
  pieces: [{ number: 1, at: 106, tickets: T(8, 7, 4) }],
  mover: { name: "Ben", color: BEN.color, piece: 1, you: true }, next: { name: "Dana", color: DANA.color, piece: null, you: false },
  ask: {
    opens_at: 0, prompt: "Wohin fährst du?", at: 106, tickets: T(8, 7, 4), seen_after: false,
    options: [...new Set(benMoves.map((m) => m.to))].sort((a, b) => a - b)
      .map((field) => ({ kind: "field" as const, field, ring: ring(field), ways: benMoves.filter((m) => m.to === field).map((m) => m.k) })),
  },
};
const danaPhone: FluechtigPhone = { ...phoneBase, hiding: hide5[1], pieces: [], mover: { ...benPhone.mover!, you: false }, next: { name: "Cem", color: CEM.color, piece: 2, you: false }, ask: null };

// ---- the proposed parts

const Y = ({ children }: { children: ReactNode }) => <b className="mk-y">{children}</b>;

/** A line as the plan draws it, for the cards and the legend. */
function Line({ k, w = 44 }: { k: Kind; w?: number }) {
  const cls = { taxi: "sp-street", bus: "sp-bus", ubahn: "sp-ubahn", schatten: "sp-ferry" }[k];
  return (
    <svg className="mk-line" width={w} height={16} viewBox={`0 0 ${w} 16`} aria-hidden="true" style={{ "--u": 1 } as CSSProperties}>
      {/* the bus drives on a street: its stripe runs down the middle of one */}
      {(k === "taxi" || k === "bus") && <line className="sp-casing" x1={4} y1={8} x2={w - 4} y2={8} />}
      {k === "bus" && <line className="sp-street" x1={4} y1={8} x2={w - 4} y2={8} />}}
      {k === "schatten" && <rect x={0} y={0} width={w} height={16} rx={4} fill="var(--river)" />}
      <line className={cls} x1={4} y1={8} x2={w - 4} y2={8} />
    </svg>
  );
}

function Ring({ k, n }: { k: Kind; n: number }) {
  return (
    <svg width={28} height={28} viewBox="-14 -14 28 28" aria-hidden="true">
      <circle className={`fl-field fl-field-${k}`} r={11} />
      <text className="fl-num" y={4} fontSize={10}>{n}</text>
    </svg>
  );
}

const Figure = ({ player, h = 64 }: { player: PlayerInfo; h?: number }) => (
  <span className="mk-figure" style={{ "--c": player.color } as CSSProperties}>
    <img src={avatarFigureUrl(player.avatar!)} alt="" height={h} />
    <b>{player.name}</b>
  </span>
);

const CARDS = 3;
function Card({ n, title, children, lift }: { n: number; title: string; children: ReactNode; lift?: string }) {
  return (
    <div className={`mk-intro ${lift ?? ""}`}>
      <div className="mk-dim" />
      <article className="mk-card">
        <small>SO GEHT FLÜCHTIG · {n} / {CARDS}</small>
        <h1>{title}</h1>
        {children}
        <footer>
          {Array.from({ length: CARDS }, (_, i) => <i key={i} className={i + 1 < n ? "mk-done" : i + 1 === n ? "mk-now" : ""} />)}
          <span>Ben kann am Handy überspringen</span>
        </footer>
      </article>
    </div>
  );
}

const cards: Record<string, ReactNode> = {
  "tv-1-who": (
    <Card n={1} title="Wer ist wer?">
      <div className="mk-cast">
        <div className="mk-side mk-side-run"><Figure player={DANA} h={74} /><p><Y>flüchtig</Y> · unsichtbar</p></div>
        <div className="mk-side"><span className="mk-row">{HUNTERS.map((h) => <Figure key={h.id} player={h} h={74} />)}</span><p><Y>Jäger</Y> · jeder mit einer Figur</p></div>
      </div>
      <ul>
        <li><span className="mk-dot" /><span>Fährt ein Jäger auf <Y>Danas Feld</Y>, ist Dana gefangen.</span></li>
        <li><span className="mk-dot" /><span>Hält Dana <Y>24 Runden</Y> durch, ist Dana <Y>entkommen</Y>.</span></li>
      </ul>
    </Card>
  ),
  "tv-2-lines": (
    <Card n={2} title="Die Linien">
      <p className="mk-lead">Jede Fahrt kostet ein <Y>Ticket</Y> und geht bis zum <Y>nächsten Halt</Y> der Linie.</p>
      <table className="mk-lines">
        <tbody>
          <tr><td><Line k="taxi" /></td><td><Ring k="taxi" n={40} /></td><td><Y>Taxi</Y> – die Straßen. Zum nächsten Feld, überall.</td></tr>
          <tr><td><Line k="bus" /></td><td><Ring k="bus" n={58} /></td><td><Y>Bus</Y> – weiter. Hält nur an <Y>grünen Ringen</Y>.</td></tr>
          <tr><td><Line k="ubahn" /></td><td><Ring k="ubahn" n={76} /></td><td><Y>U-Bahn</Y> – quer durch die Stadt. Nur an <Y>lila Ringen</Y>.</td></tr>
          <tr><td><Line k="schatten" /></td><td><Ring k="schatten" n={137} /></td><td><Y>Fähre</Y> – über den Fluss. Nur Dana, mit einer <Y>Schattenfahrt</Y>.</td></tr>
        </tbody>
      </table>
    </Card>
  ),
  "tv-3-hidden": (
    <Card n={3} title="Unsichtbar – fast immer" lift="mk-lift-log">
      <ul>
        <li><span className="mk-dot" /><span>Alle sehen nur, <Y>womit</Y> Dana fährt: Jedes Ticket kommt unten ins <Y>Fahrtenbuch</Y>.</span></li>
        <li><span className="mk-dot" /><span>In Runde <Y>3, 8, 13, 18 und 24</Y> taucht Dana <Y>auf</Y> und steht kurz auf dem Plan.</span></li>
        <li><svg width={26} height={26} viewBox="-13 -13 26 26"><circle className="fl-hideout" r={10} /></svg><span><Y>Orange Kreise</Y>: Hier kann Dana gerade sein.</span></li>
        <li><span className="mk-dot" /><span>Dana hat <Y>Schattenfahrten</Y> (Linie geheim) und <Y>Doppelzüge</Y> (zwei Fahrten am Stück).</span></li>
      </ul>
    </Card>
  ),
};

/** The legend that stays on the TV: the lines and the hideout mark. */
function Legend() {
  return (
    <div className="fl-hud mk-legend">
      {(["taxi", "ubahn", "bus", "schatten"] as Kind[]).map((k) => (
        <span key={k}><Line k={k} w={24} />{{ taxi: "Taxi", bus: "Bus", ubahn: "U-Bahn", schatten: "Fähre" }[k]}</span>
      ))}
      <span><svg width={16} height={16} viewBox="-8 -8 16 16"><circle className="fl-hideout" r={6} /></svg>Dana?</span>
    </div>
  );
}

function Tv({ scene, children }: { scene: FluechtigScene; children?: ReactNode }) {
  const root = useRef<HTMLDivElement>(null);
  // one figure each: the banner names the player, not a piece number
  useLayoutEffect(() => {
    root.current?.querySelectorAll(".fl-tc").forEach((el) => {
      const text = [...el.childNodes].find((n) => n.nodeType === Node.TEXT_NODE && / zieht Jäger \d+/.test(n.textContent ?? ""));
      if (text) text.textContent = text.textContent!.replace(/ zieht Jäger \d+/, " ist dran");
    });
  }, []);
  return (
    <div className="mk-tv mk-one" ref={root}>
      <TvFluechtig scene={scene} players={players} conn={conn} />
      {children}
    </div>
  );
}

function RoleBar({ runs }: { runs: boolean }) {
  return runs ? (
    <div className="mk-rolebar mk-rolebar-run"><b>Du bist flüchtig</b><span>Nur du siehst dein Versteck</span></div>
  ) : (
    <div className="mk-rolebar"><b>Du bist Jäger</b><span>Fang Dana!</span></div>
  );
}

/**
 * The city with bridges: the same TV over the new links, and a deck drawn under each bridge's
 * street, above the water and the ferry, from bank to bank.
 */
function BridgedTv({ scene }: { scene: FluechtigScene }) {
  const root = useRef<HTMLDivElement>(null);
  const b = boardOf(bridged as unknown as CityFile);
  useLayoutEffect(() => {
    const jetty = root.current?.querySelector(".sp-jetty");
    if (!jetty) return;
    const ns = "http://www.w3.org/2000/svg";
    for (const [n, s] of (bridged as unknown as { bridges: number[][] }).bridges) {
      const [A, B] = [b.spots[n - 1], b.spots[s - 1]];
      // the deck runs where the link is over the water, plus a little onto each bank
      const wet = (t: number) => Math.abs(A.y + (B.y - A.y) * t - riverY(b.river, A.x + (B.x - A.x) * t)) < 27;
      const ts = Array.from({ length: 201 }, (_, i) => i / 200).filter(wet);
      const at = (t: number) => `${(A.x + (B.x - A.x) * t).toFixed(1)} ${(A.y + (B.y - A.y) * t).toFixed(1)}`;
      const [edge, deck] = ([[17, "#7d6f57"], [13, "#ece4d2"]] as const).map(([w, color]) => {
        const p = document.createElementNS(ns, "path");
        p.setAttribute("d", `M${at(ts[0])} L${at(ts[ts.length - 1])}`);
        p.setAttribute("style", `fill:none;stroke:${color};stroke-width:${w}px;stroke-linecap:square`);
        return p;
      });
      jetty.after(edge);
      edge.after(deck);
    }
    // a bus drives on streets: each ride is drawn along the shortest street route between its stops
    const streets = b.links.filter((l) => l.kind === "taxi");
    const next = new Map<number, [number, number][]>();
    for (const { a, b: z } of streets) {
      const w = Math.hypot(b.spots[a - 1].x - b.spots[z - 1].x, b.spots[a - 1].y - b.spots[z - 1].y);
      next.set(a, [...(next.get(a) ?? []), [z, w]]);
      next.set(z, [...(next.get(z) ?? []), [a, w]]);
    }
    const route = (from: number, to: number) => {
      const best = new Map([[from, 0]]), back = new Map<number, number>(), open = new Set([from]);
      while (open.size) {
        const f = [...open].reduce((x, y) => (best.get(x)! <= best.get(y)! ? x : y));
        open.delete(f);
        if (f === to) break;
        for (const [n, w] of next.get(f) ?? []) {
          if ((best.get(n) ?? Infinity) > best.get(f)! + w) (best.set(n, best.get(f)! + w), back.set(n, f), open.add(n));
        }
      }
      const path = [to];
      while (path[0] !== from) path.unshift(back.get(path[0])!);
      return path;
    };
    const bus = root.current?.querySelector(".sp-bus");
    bus?.setAttribute("d", b.links.filter((l) => l.kind === "bus")
      .map((l) => route(l.a, l.b).map((f, i) => `${i ? "L" : "M"}${b.spots[f - 1].x} ${b.spots[f - 1].y}`).join("")).join(""));
    bus?.setAttribute("style", "stroke-linejoin:round;opacity:1");
  }, []);
  return (
    <div className="mk-tv mk-one" ref={root}>
      <TvFluechtig scene={{ ...scene, board: b }} players={players} conn={conn} />
      <Legend />
    </div>
  );
}

/** One figure each: the phone names the player and shows their head, not a piece number. */
function Phone({ children, theme }: { children: ReactNode; theme: "night" | "green" | "blue" }) {
  const root = useRef<HTMLElement>(null);
  useLayoutEffect(() => {
    root.current?.querySelectorAll(".flp-head b").forEach((b) => {
      b.textContent = (b.textContent ?? "").replace(/ zieht Jäger \d+/, " ist dran").replace(/^Jäger \d+ · /, "Deine Figur · ");
    });
    root.current?.querySelectorAll(".flp-disc").forEach((d) => {
      const who = players.find((p) => p.color === (d as HTMLElement).style.background || d.getAttribute("style")?.includes(p.color));
      d.textContent = "";
      const img = document.createElement("img");
      img.src = avatarHeadUrl((who ?? BEN).avatar!);
      img.style.cssText = "width:82%;height:82%;object-fit:contain";
      d.appendChild(img);
    });
  }, []);
  return <main className={`phone mk-phone mk-theme-${theme}`} ref={root}>{children}</main>;
}

const screens: Record<string, () => ReactNode> = {
  ...Object.fromEntries(Object.entries(cards).map(([k, card]) => [k, () => <Tv scene={start}>{card}</Tv>])),
  "tv-4-play-legend": () => <Tv scene={play}><Legend /></Tv>,
  "tv-5-bridges": () => <BridgedTv scene={{ ...play, targets: [], hideouts: [] }} />,
  "phone-1-role-fugitive": () => (
    <Phone theme="night">
      <section className="mk-role">
        <small>FLÜCHTIG · DEINE ROLLE</small>
        <h1>Du bist flüchtig</h1>
        <div className="mk-hide"><span className="flp-ring">37</span><p>Dein Versteck: <b>Feld 37</b><br />Nur du siehst es.</p></div>
        <ul>
          <li>Fahr <b>unsichtbar</b> durch die Stadt. Die anderen sehen nur, <b>womit</b> du fährst.</li>
          <li>Halte <b>24 Runden</b> durch, ohne dass ein Jäger auf dein Feld fährt.</li>
          <li>In Runde <b>3, 8, 13, 18 und 24</b> tauchst du auf.</li>
        </ul>
        <p className="mk-tvhint">Die Regeln laufen gerade auf dem Fernseher.</p>
      </section>
    </Phone>
  ),
  ...Object.fromEntries((["green", "blue"] as const).flatMap((theme) => [
    [`phone-2-role-hunter-${theme}`, () => <HunterRole theme={theme} />],
    [`phone-3-hunter-moves-${theme}`, () => <Phone theme={theme}><RoleBar runs={false} /><PhoneFluechtig phone={benPhone} me={BEN} conn={conn} /></Phone>],
  ])),
  "phone-4-fugitive-waits": () => <Phone theme="night"><RoleBar runs /><PhoneFluechtig phone={danaPhone} me={DANA} conn={conn} /></Phone>,
};

function HunterRole({ theme }: { theme: "green" | "blue" }) {
  return (
    <Phone theme={theme}>
      <section className="mk-role">
        <small>FLÜCHTIG · DEINE ROLLE</small>
        <h1>Du bist Jäger</h1>
        <div className="mk-mine"><img src={avatarFigureUrl(BEN.avatar!)} alt="" height={84} /><p>Deine Figur steht<br />auf <b>Feld {startAt[0]}</b></p></div>
        <ul>
          <li>Fang <b>Dana</b>: Fahr auf das Feld, auf dem Dana steckt.</li>
          <li>Dana ist unsichtbar. Die <b>orangen Kreise</b> auf dem Fernseher zeigen, wo Dana sein kann.</li>
          <li><b>Cem</b> und <b>Ela</b> jagen mit dir.</li>
        </ul>
        <p className="mk-tvhint">Die Regeln laufen gerade auf dem Fernseher.</p>
        <button className="flp-back mk-skip">Regeln überspringen</button>
      </section>
    </Phone>
  );
}

const pick = screens[location.hash.slice(1)];
document.documentElement.dataset.surface = location.hash.startsWith("#tv") ? "tv" : "phone";
createRoot(document.getElementById("root")!).render(
  pick ? pick() : <ul className="mk-index">{Object.keys(screens).map((k) => <li key={k}><a href={`#${k}`} onClick={() => setTimeout(() => location.reload())}>{k}</a></li>)}</ul>,
);
