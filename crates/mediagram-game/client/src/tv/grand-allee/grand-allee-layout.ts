// Grand Allee's board on the TV. tools/grand-allee-models/board_path.py is the one source of the board's geometry; the client's
// copy, public/grand-allee/layout.json, is generated from it and never edited by hand. From crates/mediagram-game/client:
//   python3 ../tools/grand-allee-models/board_path.py public/grand-allee
// The layout is in metres, x east and y north. three.js has y up, so board (x, y) is three's (x, -y): the TV looks north.

type Pt = [number, number];
export interface LayoutJson {
  board: Pt;
  road: number;
  /** `outline`: the field along the curve, its left edge forward, then its right edge back (board_land.field_outline). */
  fields: { id: number; x: number; y: number; heading: number; corners: Pt[]; outline: Pt[] }[];
  /** `land`: from the road's edge on the plot's `side` along its frontage, then back round the hotel (board_land.plot_land). */
  plots: { hotel: number; name: string; x: number; y: number; first: number; last: number; side: "park" | "outer"; w: number; d: number; rotation: number; scale: number; land: Pt[] }[];
  sea_y: number;
  river: Pt[];
  river_width: number;
  pond: [number, number, number];
}
/** A point on the ground, in three's coordinates. */
export interface Spot {
  x: number;
  z: number;
}
export interface Field extends Spot {
  /** three's turn about y that lays local +x along the walk (the board's heading, with y north being -z). */
  rot: number;
  outline: Spot[];
  /** The field's middle line from its start to its end, one point per pair of edge points; the walk follows it. */
  mid: Spot[];
}
export interface Plot extends Spot {
  hotel: number;
  /** The hotel's model: public/grand-allee/<name>.glb. */
  name: string;
  w: number;
  d: number;
  rot: number;
  scale: number;
  /** The frontage, first and last field. */
  first: number;
  last: number;
  /** The side of the walk the plot lies on: 1 right (the park, inside the loop), -1 left (outside it). */
  side: 1 | -1;
  /** The plot's land, a closed polygon touching the road along the frontage. */
  land: Spot[];
}
export interface Board {
  json: LayoutJson;
  fields: Field[];
  plots: Plot[];
  width: number;
  depth: number;
}

export const toSpot = ([x, y]: readonly number[]): Spot => ({ x, z: -y });

export function boardOf(json: LayoutJson): Board {
  const fields = [...json.fields].sort((a, b) => a.id - b.id).map((f) => {
    const outline = f.outline.map((c) => toSpot(c)), n = outline.length / 2;
    const mid = Array.from({ length: n }, (_, k) => {
      const a = outline[k], b = outline[outline.length - 1 - k];
      return { x: (a.x + b.x) / 2, z: (a.z + b.z) / 2 };
    });
    return { ...toSpot([f.x, f.y]), rot: f.heading, outline, mid };
  });
  const plots = [...json.plots].sort((a, b) => a.hotel - b.hotel).map((p) => ({
    ...toSpot([p.x, p.y]), hotel: p.hotel, name: p.name, w: p.w, d: p.d, rot: p.rotation, scale: p.scale,
    first: p.first, last: p.last, side: (p.side === "park" ? 1 : -1) as 1 | -1, land: p.land.map((c) => toSpot(c)),
  }));
  return { json, fields, plots, width: json.board[0], depth: json.board[1] };
}

const wrap = (i: number, n: number) => ((i % n) + n) % n;
/** The field `u` is on (fractions round to the nearest, wraps allowed). */
export const fieldAt = (b: Board, u: number): Field => b.fields[wrap(Math.round(u), b.fields.length)];

/**
 * The road's middle line `u` fields on from field 0's centre (fractions and wraps allowed), along the curve: from a field's
 * centre on through its second half and the next field's first half.
 */
export function fieldPoint(b: Board, u: number): Spot {
  const n = b.fields.length, k = Math.floor(u), here = b.fields[wrap(k, n)], steps = here.mid.length - 1, j = steps / 2 + (u - k) * steps;
  const field = j <= steps ? here : b.fields[wrap(k + 1, n)], at = j <= steps ? j : j - steps;
  const i = Math.min(Math.floor(at), steps - 1), g = at - i, p = field.mid[i], q = field.mid[i + 1];
  return { x: p.x + (q.x - p.x) * g, z: p.z + (q.z - p.z) * g };
}

export async function loadLayout(url: string): Promise<Board> {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`layout: ${r.status}`);
  return boardOf((await r.json()) as LayoutJson);
}
