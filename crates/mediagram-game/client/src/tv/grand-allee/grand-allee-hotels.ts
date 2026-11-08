// The ten hotels on their plots. Each GLB holds stage_<n> empties with a stage_<n>_body mesh and, for most stages, a
// stage_<n>_tint mesh. Per hotel the bodies are merged in stage order into one mesh and the tints into another, so stages
// 1..levels are one draw range each: two draw calls a hotel, whatever is built. The tint is baked white and multiplied by the
// owner's colour (the material's colour). A stage being built rises inside a clipping plane on a copy sharing the geometry.
// A free plot shows nothing: the board has the plot edging.

import * as THREE from "three";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import type { Plot } from "./grand-allee-layout";
import { TIMES } from "./grand-allee-look";

/** [start, count] in a merged mesh's indices. */
export type Range = [number, number];
type BasicMesh = THREE.Mesh<THREE.BufferGeometry, THREE.MeshBasicMaterial>;
export interface Part {
  mesh: BasicMesh;
  /** The stage rising, drawn alone inside the hotel's clipping plane. */
  rise: BasicMesh;
  /** Each stage's range, in stage order. */
  ranges: Range[];
}
export interface HotelView {
  body: Part | null;
  tint: Part | null;
  /** Each stage's bottom and top, metres above the board. */
  heights: [number, number][];
  clip: THREE.Plane;
  built: number;
  rising: { stage: number; t0: number } | null;
  color: string;
  seen: boolean;
}

/** The index range of stages 1..n: the stages lie in order, so it always starts at 0. */
export function prefix(ranges: Range[], n: number): Range {
  return [0, ranges.slice(0, Math.max(0, n)).reduce((a, r) => a + r[1], 0)];
}

// a geometry of its own (its own draw range) over the same buffers, so the GPU holds them once
function shared(g: THREE.BufferGeometry) {
  const s = new THREE.BufferGeometry();
  s.setIndex(g.index);
  for (const [name, a] of Object.entries(g.attributes)) s.setAttribute(name, a);
  s.boundingSphere = g.boundingSphere;
  return s;
}

function part(root: THREE.Object3D, kind: "body" | "tint", stages: number, place: THREE.Matrix4, clip: THREE.Plane, heights: [number, number][]): Part | null {
  const geos: THREE.BufferGeometry[] = [], ranges: Range[] = [], m = new THREE.Matrix4();
  let at = 0, material: THREE.MeshBasicMaterial | null = null;
  for (let n = 1; n <= stages; n++) {
    const meshes: THREE.Mesh[] = [];
    root.getObjectByName(`stage_${n}_${kind}`)?.traverse((o) => {
      if ((o as THREE.Mesh).isMesh) meshes.push(o as THREE.Mesh);
    });
    let count = 0;
    for (const mesh of meshes) {
      const g = mesh.geometry.clone();
      for (const name of Object.keys(g.attributes)) if (name !== "position" && name !== "uv") g.deleteAttribute(name);
      if (!g.index) g.setIndex(Array.from({ length: g.getAttribute("position").count }, (_, i) => i));
      g.applyMatrix4(m.multiplyMatrices(place, mesh.matrixWorld));
      g.computeBoundingBox();
      const h = heights[n - 1], box = g.boundingBox!;
      [h[0], h[1]] = [Math.min(h[0], box.min.y), Math.max(h[1], box.max.y)];
      geos.push(g);
      count += g.index!.count;
      material ??= mesh.material as THREE.MeshBasicMaterial;
    }
    ranges.push([at, count]);
    at += count;
  }
  if (!material || !geos.length) return null;
  const geo = mergeGeometries(geos);
  if (!geo) throw new Error(`hotel ${kind}: the stages' meshes do not merge`);
  geo.computeBoundingSphere();
  const cut = material.clone();
  cut.clippingPlanes = [clip];
  const mesh = new THREE.Mesh(geo, material.clone()), rise = new THREE.Mesh(shared(geo), cut);
  mesh.visible = rise.visible = false;
  return { mesh, rise, ranges };
}

/** Places a hotel's model on its plot, scaled and facing the TV; `stages`: how many the rules give it. */
export function buildHotel(scene: THREE.Scene, root: THREE.Object3D, plot: Plot, stages: number): HotelView {
  root.updateMatrixWorld(true);
  const turn = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), plot.rot);
  const place = new THREE.Matrix4().compose(new THREE.Vector3(plot.x, 0, plot.z), turn, new THREE.Vector3().setScalar(plot.scale));
  const clip = new THREE.Plane(new THREE.Vector3(0, -1, 0), 0), heights = Array.from({ length: stages }, (): [number, number] => [Infinity, -Infinity]);
  const body = part(root, "body", stages, place, clip, heights), tint = part(root, "tint", stages, place, clip, heights);
  for (const p of [body, tint]) if (p) scene.add(p.mesh, p.rise);
  return { body, tint, heights, clip, built: 0, rising: null, color: "", seen: false };
}

const smooth = (x: number) => {
  const t = Math.min(1, Math.max(0, x));
  return t * t * (3 - 2 * t);
};
function show(p: Part | null, built: number, rising: number | null) {
  if (!p) return;
  const r = prefix(p.ranges, built), s = rising === null ? undefined : p.ranges[rising];
  p.mesh.geometry.setDrawRange(...r);
  // a mesh with nothing to draw still costs the box a draw call's set-up: leave it out
  p.mesh.visible = r[1] > 0;
  p.rise.visible = s !== undefined && s[1] > 0;
  if (s) p.rise.geometry.setDrawRange(...s);
}

/** Moves a hotel on to frame time `t` (s): `levels` stages, owned by a player in `color` (null: free). */
export function stepHotel(h: HotelView, levels: number, color: string | null, t: number) {
  // first sight, or fewer stages than shown (sold back, a new game): as it stands, nothing rising
  if (!h.seen || levels < h.built) {
    h.built = levels;
    h.rising = null;
    h.seen = true;
  }
  if (h.rising && t - h.rising.t0 >= TIMES.rise) {
    h.built = h.rising.stage + 1;
    h.rising = null;
  }
  if (!h.rising && levels > h.built) h.rising = { stage: h.built, t0: t };
  if (color && color !== h.color) {
    h.color = color;
    h.tint?.mesh.material.color.set(color);
    h.tint?.rise.material.color.set(color);
  }
  const r = h.rising;
  show(h.body, h.built, r?.stage ?? null);
  show(h.tint, h.built, r?.stage ?? null);
  const [lo, hi] = (r && h.heights[r.stage]) || [0, 0];
  if (r && Number.isFinite(lo)) h.clip.constant = lo + (hi - lo + 0.1) * smooth((t - r.t0) / TIMES.rise);
}
