import { useServerNow, type Connection } from "../net";
import type { GrandAlleeScene } from "../protocol/GrandAlleeScene";
import type { GrandAlleeTurn } from "../protocol/GrandAlleeTurn";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { TvFinal } from "./TvFinal";

/*
 * Stand-in debug board for Grand Allee: plain SVG, no art, no animation, so the live game can be played
 * and checked before the three.js board replaces this file. Field ids on the board are shown 1-based, as the phones
 * name them ("Feld 5").
 */

const LETTER = { bank: "B", rathaus: "R", plot: "P", site: "S", free_entrance: "E", free_build: "G", allee: "·" } as const;
const PERMIT = { approved: "Genehmigt", rejected: "Abgelehnt", free: "Gratis", double: "Doppelt" } as const;
const MARK = { blue: "var(--blue)", orange: "var(--orange)", yellow: "var(--yellow)", pink: "var(--pink)" } as const;
const [W, H, RADIUS, STEP] = [1600, 760, 200, 68];
const [WEST, EAST, MID] = [W / 2 - 8 * STEP, W / 2 + 8 * STEP, H / 2];

/** Field `i` on the loop (straights 4–19 and 24–39, curves 0–3 and 20–23); `out` moves it off the loop. */
const spot = (i: number, out = 0) => {
  if (i >= 4 && i <= 19) return { x: WEST + (i - 3.5) * STEP, y: MID - RADIUS - out };
  if (i >= 24) return { x: EAST - (i - 23.5) * STEP, y: MID + RADIUS + out };
  const [cx, deg] = i < 4 ? [WEST, 90 + (i + 0.5) * 45] : [EAST, -90 + (i - 19.5) * 45];
  const a = (deg * Math.PI) / 180;
  return { x: cx + (RADIUS + out) * Math.cos(a), y: MID + (RADIUS + out) * Math.sin(a) };
};
/** Light text with a dark outline, readable on any owner colour. */
const LEGIBLE = { fill: "var(--card)", stroke: "var(--ink)", strokeWidth: 3, paintOrder: "stroke" } as const;
const nights = (n: number) => `${n} ${n === 1 ? "Nacht" : "Nächte"}`;
const taler = (n: number) => `${n.toLocaleString("de-DE")} Taler`;
const secs = (until: number, now: number) => `${Math.max(0, Math.ceil((until - now) / 1000))} s`;

function describe(turn: GrandAlleeTurn, scene: GrandAlleeScene, name: (id: number) => string, now: number): string {
  const hotel = (h: number) => scene.board.hotels[h].name;
  switch (turn.stage) {
    case "ask":
      return `${name(turn.player)}: ${turn.prompt} · ${secs(turn.closes_at, now)}`;
    case "rolled":
      return now < turn.lands_at ? `${name(turn.player)} würfelt …` : `${name(turn.player)} würfelt eine ${turn.value}.`;
    case "walking":
      return `${name(turn.player)} läuft ${turn.value}${turn.bank ? " und kassiert an der Bank" : ""}.`;
    case "guest":
      return `${name(turn.player)} zahlt ${name(turn.owner)} ${taler(turn.amount)} für ${nights(turn.nights)} im ${hotel(turn.hotel)}.`;
    case "permit":
      return now < turn.lands_at ? "Genehmigung: der Würfel rollt …" : `Genehmigung ${hotel(turn.hotel)}: ${PERMIT[turn.permit]}`;
    case "auction": {
      const a = turn.auction;
      const who = (ids: number[]) => ids.map(name).join(", ") || "–";
      return `Versteigerung ${hotel(a.hotel)} (Wert ${taler(a.value)}) · ${secs(a.closes_at, now)} · geboten: ${who(a.bid)} · gepasst: ${who(a.passed)}`;
    }
    case "sold":
      return turn.winner === null
        ? `Kein Gebot: Die Bank kauft ${hotel(turn.hotel)} für ${taler(turn.price)}.`
        : `${name(turn.winner)} ersteigert ${hotel(turn.hotel)} für ${taler(turn.price)}.`;
    case "note":
      return turn.text;
    case "final":
      return "";
  }
}

export function TvGrandAllee({ scene, players, conn }: { scene: GrandAlleeScene; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const byId = new Map(players.map((p) => [p.id, p]));
  const name = (id: number) => byId.get(id)?.name ?? "Jemand";
  const color = (id: number) => byId.get(id)?.color ?? "#888";
  const { turn, board } = scene;
  if (turn.stage === "final") {
    const order = new Map(turn.ranking.map((id, i) => [id, i]));
    const ranked = players.filter((p) => order.has(p.id)).sort((a, b) => order.get(a.id)! - order.get(b.id)!);
    return <TvFinal players={ranked} conn={conn} ranked />;
  }
  const owner = new Map(scene.estates.map((e) => [e.hotel, e]));
  const marks = turn.stage === "ask" ? turn.marks : [];
  // outer hotels (sea and town side) stand outside the loop, the park hotels inside it
  const side = (h: number) => (h < 6 ? 1 : -1);
  const dice = [scene.die && `Würfel ${scene.die}`, scene.permit && PERMIT[scene.permit], scene.nights && nights(scene.nights)];
  return (
    <section style={{ height: "100%", display: "grid", gridTemplateRows: "1fr auto", gap: "1vh" }}>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", height: "100%" }} fontFamily="var(--body)">
        {board.hotels.map((h) => {
          const e = owner.get(h.id);
          const p = spot((h.first + h.last) / 2, side(h.id) > 0 ? 80 : -95);
          return (
            <g key={h.id} transform={`translate(${p.x} ${p.y})`}>
              <rect x={-95} y={-26} width={190} height={52} rx={8} fill={e ? color(e.owner) : "var(--felt-deep)"} stroke="var(--felt-line)" />
              <text textAnchor="middle" y={-3} fontSize={20} {...LEGIBLE}>{h.name}</text>
              <text textAnchor="middle" y={19} fontSize={16} {...LEGIBLE}>{e ? `Stufe ${e.levels}/${h.stages.length}` : `frei · ${taler(h.land)}`}</text>
            </g>
          );
        })}
        {board.fields.map((kind, i) => {
          const p = spot(i);
          const mark = marks.find((m) => m.field === i);
          return (
            <g key={i}>
              <circle cx={p.x} cy={p.y} r={26} fill="var(--card)" stroke={mark ? MARK[mark.color] : "var(--card-edge)"} strokeWidth={mark ? 6 : 2} />
              <text x={p.x} y={p.y + 2} textAnchor="middle" fontSize={20} fontWeight={700} fill="var(--ink)">{LETTER[kind]}</text>
              <text x={p.x} y={p.y + 20} textAnchor="middle" fontSize={11} fill="var(--ink)">{i + 1}</text>
            </g>
          );
        })}
        {scene.entrances.map((en) => {
          const p = spot(en.field, side(en.hotel) * 48);
          const e = owner.get(en.hotel);
          return <rect key={en.field} x={p.x - 9} y={p.y - 9} width={18} height={18} fill={e ? color(e.owner) : "#888"} stroke="var(--card)" />;
        })}
        {scene.seats.filter((s) => !s.out).map((s) => {
          const p = spot(s.field);
          return <circle key={s.player} cx={p.x} cy={p.y} r={31} fill="none" stroke={color(s.player)} strokeWidth={6} />;
        })}
        <text x={W / 2} y={H / 2 - 40} textAnchor="middle" fontSize={34} fontFamily="var(--display)" fill="var(--card)">
          Runde {scene.round} von {scene.rounds}
        </text>
        <text x={W / 2} y={H / 2 + 4} textAnchor="middle" fontSize={22} fill="var(--card)">{describe(turn, scene, name, now)}</text>
        <text x={W / 2} y={H / 2 + 40} textAnchor="middle" fontSize={18} fill="var(--muted)">{dice.filter(Boolean).join(" · ")}</text>
      </svg>
      <footer style={{ display: "flex", gap: "2vw", justifyContent: "center", flexWrap: "wrap" }}>
        {scene.seats.map((s) => (
          <span key={s.player} style={{ opacity: s.out ? 0.4 : 1, borderBottom: `0.3rem solid ${color(s.player)}`, fontSize: "1.3rem" }}>
            {name(s.player)}: {taler(s.cash)} · Vermögen {taler(s.worth)}
          </span>
        ))}
      </footer>
    </section>
  );
}
