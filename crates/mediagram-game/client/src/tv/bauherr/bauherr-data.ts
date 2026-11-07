// The baked town as the TV reads it: world.json indexes world.bin (positions and uvs as floats, light as bytes, indices
// as 16 or 32 bits), written by tools/bauherr-bake. Everything is checked on the way in; a file from another bake
// fails loudly here rather than drawing garbage.

import * as THREE from "three";

/** The baked light can exceed 1: bytes carry light / LIGHT_MAX and the materials multiply it back. */
export const LIGHT_MAX = 1.25;
type Range = [number, number];

export interface MeshRec {
  n: number;
  pos: number;
  uv: number;
  col: number;
  tint: number;
  idx: number;
  count: number;
  u32: boolean;
}
export interface StageRec {
  /** Index range [start, count] of the stage in the hotel's base mesh, in its owner mesh, and its scaffold. */
  p: Range;
  o: Range;
  scaf: Range;
  shadow: Range;
  /** Height of the stage and of its scaffold, world units. */
  top: number;
  scafTop: number;
  /** y: walls rise from the ground; x: a facility is laid out along the lot from x0 to x1 (world units from the lot centre). */
  axis: "y" | "x";
  x0: number;
  x1: number;
}
export interface HotelRec {
  plot: number;
  name: string;
  x: number;
  z: number;
  rot: number;
  /** The lot's local +x in the world (x, z). */
  dir: [number, number];
  image: string;
  base: MeshRec;
  owner: MeshRec;
  shadow: MeshRec;
  /** Ranges in the base mesh: the for-sale sign, the raw lot ground (both before the first stage). */
  sale: Range;
  overlay: Range;
  /** The property line and plot sign in the owner mesh. */
  own0: Range;
  /** The plot sign in the owner mesh, which stands only while no stage is built. */
  sign: Range;
  stages: StageRec[];
}
export interface WorldRec {
  hotels: HotelRec[];
  fields: [number, number, number][];
  /** Glow discs: x, y, z, radius, strength, colour. */
  halos: [number, number, number, number, number, string][];
  town: MeshRec;
  /** Mountains, hill and coast rocks: lit at bake time, one image. */
  terrain: MeshRec;
  canopy: MeshRec;
  /** The baked light on an upward surface (linear rgb), which the ground print is multiplied by. */
  up: [number, number, number];
  ground: { x0: number; x1: number; z0: number; z1: number };
  /** The land beyond the ground print, a small image over a large rectangle. */
  outer: { x0: number; x1: number; z0: number; z1: number };
}

const isRange = (r: unknown): r is Range => Array.isArray(r) && r.length === 2 && r.every((n) => Number.isInteger(n) && n >= 0);

/** Checks the shape of world.json against what the TV needs, and that every index range lies inside its mesh. */
export function parseWorld(raw: unknown, hotelStages: number[]): WorldRec {
  const w = raw as WorldRec;
  if (!w || !Array.isArray(w.hotels) || w.hotels.length !== hotelStages.length) throw new Error("world.json: wrong number of hotels");
  const inside = (r: Range, m: MeshRec, what: string) => {
    if (!isRange(r) || r[0] + r[1] > m.count) throw new Error(`world.json: ${what} range outside its mesh`);
  };
  w.hotels.forEach((h, i) => {
    if (h.plot !== i || h.stages.length !== hotelStages[i]) throw new Error(`world.json: hotel ${i} has ${h.stages.length} stages, the rules have ${hotelStages[i]}`);
    inside(h.sale, h.base, "sale");
    inside(h.overlay, h.base, "overlay");
    inside(h.own0, h.owner, "own0");
    inside(h.sign, h.owner, "sign");
    h.stages.forEach((s, k) => {
      inside(s.p, h.base, `hotel ${i} stage ${k}`);
      inside(s.scaf, h.base, `hotel ${i} scaffold ${k}`);
      inside(s.o, h.owner, `hotel ${i} owner pieces ${k}`);
      inside(s.shadow, h.shadow, `hotel ${i} shadow ${k}`);
    });
  });
  if (!Array.isArray(w.fields) || w.fields.length !== 36 || !Array.isArray(w.up) || w.up.length !== 3) throw new Error("world.json: fields or light missing");
  if (!w.terrain || !w.town || !w.canopy) throw new Error("world.json: a mesh is missing");
  return w;
}

/** A mesh's buffers as a geometry; `color` is normalised bytes, `aTint` (owner pieces) the 0/1 tint flag. */
export function geometryOf(buf: ArrayBuffer, m: MeshRec): THREE.BufferGeometry {
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.BufferAttribute(new Float32Array(buf, m.pos, m.n * 3), 3));
  g.setAttribute("uv", new THREE.BufferAttribute(new Float32Array(buf, m.uv, m.n * 2), 2));
  if (m.col >= 0) g.setAttribute("color", new THREE.BufferAttribute(new Uint8Array(buf, m.col, m.n * 3), 3, true));
  if (m.tint >= 0) g.setAttribute("aTint", new THREE.BufferAttribute(new Uint8Array(buf, m.tint, m.n), 1, false));
  g.setIndex(new THREE.BufferAttribute(m.u32 ? new Uint32Array(buf, m.idx, m.count) : new Uint16Array(buf, m.idx, m.count), 1));
  return g;
}
/** A second geometry over the same buffers, so a stage can be drawn on its own (rising, in a clip) without copying anything. */
export function shareGeometry(g: THREE.BufferGeometry): THREE.BufferGeometry {
  const s = new THREE.BufferGeometry();
  for (const [name, a] of Object.entries(g.attributes)) s.setAttribute(name, a);
  s.setIndex(g.index);
  return s;
}
