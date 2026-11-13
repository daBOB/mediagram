import type { FluechtigBoard } from "../../protocol/FluechtigBoard";
import type { FluechtigSpot } from "../../protocol/FluechtigSpot";
import type { FluechtigTicket } from "../../protocol/FluechtigTicket";
import { ferryLane, riverY, smoothPath } from "./fluechtig-river";
import { bridgeDecks, streetRoutes } from "./street-routes";

/** How far a U-Bahn link bows, as a share of its length: it must not run over the fields between its stops. */
const BOW = 0.09;
/** Which transport marks a field's ring: U-Bahn over bus over a ferry pier over taxi. */
const RANK: Record<FluechtigTicket, number> = { taxi: 0, schatten: 1, bus: 2, ubahn: 3 };

export function linkPath(a: FluechtigSpot, b: FluechtigSpot, bow = 0) {
  if (!bow) return `M${a.x} ${a.y}L${b.x} ${b.y}`;
  const [dx, dy] = [b.x - a.x, b.y - a.y];
  return `M${a.x} ${a.y}Q${((a.x + b.x) / 2 - dy * bow).toFixed(1)} ${((a.y + b.y) / 2 + dx * bow).toFixed(1)} ${b.x} ${b.y}`;
}

/**
 * Every line of the plan that never changes, one SVG path per kind. A bus
 * drives along the streets between its stops; the bridges' decks lie under
 * the streets that cross the river.
 */
export function planLines(board: FluechtigBoard) {
  const at = (field: number) => board.spots[field - 1];
  const paths = (kind: FluechtigTicket, bow: number) =>
    board.links
      .filter((l) => l.kind === kind)
      .map((l) => linkPath(at(l.a), at(l.b), bow))
      .join("");
  // a link of the shadow ride's own kind is the ferry: only that ticket boards it
  const ferry = board.links.filter((l) => l.kind === "schatten");
  const rides = board.links.filter((l) => l.kind === "bus");
  return {
    river: smoothPath(board.river),
    streets: paths("taxi", 0),
    bus: streetRoutes(board, rides)
      .map((route) => route.map((f, i) => `${i ? "L" : "M"}${at(f).x} ${at(f).y}`).join(""))
      .join(""),
    ubahn: paths("ubahn", BOW),
    ferry: ferry.map((l) => ferryLane(board.river, at(l.a), at(l.b))).join(""),
    jetties: ferry
      .flatMap((l) => [at(l.a), at(l.b)])
      .map((s) => `M${s.x} ${s.y}V${riverY(board.river, s.x).toFixed(1)}`)
      .join(""),
    bridges: bridgeDecks(board),
  };
}

/** The strongest transport at each field, which rings it. */
export function fieldRings(board: FluechtigBoard) {
  const ring = new Map<number, FluechtigTicket>();
  for (const l of board.links) {
    for (const f of [l.a, l.b]) if (RANK[l.kind] > RANK[ring.get(f) ?? "taxi"]) ring.set(f, l.kind);
  }
  return ring;
}
