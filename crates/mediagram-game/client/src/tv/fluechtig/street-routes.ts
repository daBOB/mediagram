import type { FluechtigBoard } from "../../protocol/FluechtigBoard";
import type { FluechtigSpot } from "../../protocol/FluechtigSpot";
import { riverY } from "./fluechtig-river";

/** How far from the river's middle a bridge's deck reaches: the water plus a little bank on each side. */
const DECK_REACH = 27;

const dist = (p: FluechtigSpot, q: FluechtigSpot) => Math.hypot(p.x - q.x, p.y - q.y);

/**
 * For each ride, the fields of the shortest street (taxi) route from its `a`
 * to its `b`, measured on the plan: a bus drives along streets, never
 * through a block. A ride with no street route is drawn straight.
 */
export function streetRoutes(board: FluechtigBoard, rides: { a: number; b: number }[]): number[][] {
  const at = (f: number) => board.spots[f - 1];
  const next = new Map<number, [number, number][]>();
  for (const { a, b, kind } of board.links) {
    if (kind !== "taxi") continue;
    const w = dist(at(a), at(b));
    next.set(a, [...(next.get(a) ?? []), [b, w]]);
    next.set(b, [...(next.get(b) ?? []), [a, w]]);
  }
  const route = (from: number, to: number) => {
    const best = new Map([[from, 0]]);
    const back = new Map<number, number>();
    const open = new Set([from]);
    while (open.size) {
      const f = [...open].reduce((x, y) => (best.get(x)! <= best.get(y)! ? x : y));
      open.delete(f);
      if (f === to) break;
      for (const [n, w] of next.get(f) ?? []) {
        if ((best.get(n) ?? Infinity) > best.get(f)! + w) {
          best.set(n, best.get(f)! + w);
          back.set(n, f);
          open.add(n);
        }
      }
    }
    if (!best.has(to)) return [from, to];
    const path = [to];
    while (path[0] !== from) path.unshift(back.get(path[0])!);
    return path;
  };
  return rides.map(({ a, b }) => route(a, b));
}

/**
 * The bridges' decks, one straight segment each: wherever a street or bus
 * ride joins fields on opposite banks, the stretch of it within the river's
 * reach. The U-Bahn runs under the river and the ferry on it: no deck.
 */
export function bridgeDecks(board: FluechtigBoard): string {
  const at = (f: number) => board.spots[f - 1];
  const north = (f: number) => at(f).y < riverY(board.river, at(f).x);
  const pairs = new Map<string, [FluechtigSpot, FluechtigSpot]>();
  for (const { a, b, kind } of board.links) {
    if ((kind === "taxi" || kind === "bus") && north(a) !== north(b)) pairs.set(`${Math.min(a, b)}-${Math.max(a, b)}`, [at(a), at(b)]);
  }
  return [...pairs.values()]
    .map(([A, B]) => {
      const point = (t: number) => ({ x: A.x + (B.x - A.x) * t, y: A.y + (B.y - A.y) * t });
      const wet = Array.from({ length: 201 }, (_, i) => i / 200).filter((t) => Math.abs(point(t).y - riverY(board.river, point(t).x)) < DECK_REACH);
      if (!wet.length) return "";
      const [p, q] = [point(wet[0]), point(wet[wet.length - 1])];
      return `M${p.x.toFixed(1)} ${p.y.toFixed(1)}L${q.x.toFixed(1)} ${q.y.toFixed(1)}`;
    })
    .join("");
}
