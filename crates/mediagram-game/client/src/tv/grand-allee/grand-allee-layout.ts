// Grand Allee's board on the TV. tools/grand-allee-models/board_path.py is the one source of the board's geometry; the client's
// copy, public/grand-allee/layout.json, is generated from it and never edited by hand. From crates/mediagram-game/client:
//   python3 ../tools/grand-allee-models/board_path.py public/grand-allee
// The layout is in metres, x east and y north. three.js has y up, so board (x, y) is three's (x, -y): the TV looks north.

export interface LayoutJson {
  board: [number, number];
  road: number;
  fields: { id: number; x: number; y: number; heading: number; corners: [number, number][] }[];
  plots: { hotel: number; name: string; x: number; y: number; w: number; d: number; rotation: number; scale: number }[];
  sea_y: number;
  river: [number, number][];
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
  corners: Spot[];
}
export interface Plot extends Spot {
  hotel: number;
  /** The hotel's model: public/grand-allee/<name>.glb. */
  name: string;
  w: number;
  d: number;
  rot: number;
  scale: number;
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
  const fields = [...json.fields].sort((a, b) => a.id - b.id).map((f) => ({ ...toSpot([f.x, f.y]), rot: f.heading, corners: f.corners.map((c) => toSpot(c)) }));
  const plots = [...json.plots]
    .sort((a, b) => a.hotel - b.hotel)
    .map((p) => ({ ...toSpot([p.x, p.y]), hotel: p.hotel, name: p.name, w: p.w, d: p.d, rot: p.rotation, scale: p.scale }));
  return { json, fields, plots, width: json.board[0], depth: json.board[1] };
}

const wrap = (i: number, n: number) => ((i % n) + n) % n;
/** The field `u` is on (fractions round to the nearest, wraps allowed). */
export const fieldAt = (b: Board, u: number): Field => b.fields[wrap(Math.round(u), b.fields.length)];

/** The road `u` fields on from field 0 (fractions and wraps allowed): straight from one field's centre to the next. */
export function fieldPoint(b: Board, u: number): Spot {
  const n = b.fields.length, k = Math.floor(u), f = u - k, a = b.fields[wrap(k, n)], c = b.fields[wrap(k + 1, n)];
  return { x: a.x + (c.x - a.x) * f, z: a.z + (c.z - a.z) * f };
}

export async function loadLayout(url: string): Promise<Board> {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`layout: ${r.status}`);
  return boardOf((await r.json()) as LayoutJson);
}
