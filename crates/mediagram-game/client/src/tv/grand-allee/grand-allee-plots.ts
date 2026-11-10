// Ownership on the ground: a frame along the land of every owned plot, from the road's edge round the hotel, in its owner's colour
// (an unbuilt plot shows its owner too; a free one shows nothing over the board's own edging), flashing when the plot changes
// hands, and the entrances: a small gate at the field's road edge on the hotel's side, in the owner's colour, dropping in when
// placed. One draw call each.

import * as THREE from "three";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { shade } from "./grand-allee-board";
import type { Board, Spot } from "./grand-allee-layout";
import { GROUND, TIMES } from "./grand-allee-look";

/** Metres: the frame's width inside the land's edge; a free plot's frame waits under the board. */
export const FRAME = { width: 0.7, y: 0.14, hidden: -4 };
/** Metres from the road's centre line to a gate: on the field, at its edge. */
export const GATE_EDGE = 2.3;

/** The polygon `w` metres inside the closed polygon `poly` (either winding), mitred at each corner, at most three widths deep. */
export function inset(poly: Spot[], w: number): Spot[] {
  const n = poly.length, turn = Math.sign(poly.reduce((a, p, i) => a + p.x * poly[(i + 1) % n].z - poly[(i + 1) % n].x * p.z, 0)) || 1;
  // the inward normal of the edge p → q: on the left of it in a counter-clockwise polygon
  const normal = (p: Spot, q: Spot) => {
    const dx = q.x - p.x, dz = q.z - p.z, l = Math.hypot(dx, dz) || 1;
    return { x: (-dz / l) * turn, z: (dx / l) * turn };
  };
  return poly.map((p, i) => {
    const a = normal(poly[(i + n - 1) % n], p), b = normal(p, poly[(i + 1) % n]), mx = a.x + b.x, mz = a.z + b.z, ml = Math.hypot(mx, mz) || 1;
    const depth = Math.min(3 * w, w / Math.max(1e-3, (mx / ml) * b.x + (mz / ml) * b.z));
    return { x: p.x + (mx / ml) * depth, z: p.z + (mz / ml) * depth };
  });
}

function gateGeometry() {
  const box = (w: number, h: number, d: number, x: number, y: number) => new THREE.BoxGeometry(w, h, d).translate(x, y, 0);
  const merged = mergeGeometries([box(3.4, 0.16, 1.3, 0, 0.08), box(0.3, 2.8, 0.3, -1.5, 1.4), box(0.3, 2.8, 0.3, 1.5, 1.4), box(3.8, 0.45, 1.5, 0, 2.95)]);
  if (!merged) throw new Error("gate geometry");
  return shade(merged);
}

export class Grounds {
  readonly frames: THREE.Mesh;
  readonly gates: THREE.InstancedMesh;
  /** Each plot's frame vertices, [first, count]: a point on the land's edge and its inset partner, in turn. */
  readonly ranges: [number, number][];
  private pos: Float32Array;
  private col: Float32Array;
  private c = new THREE.Color();
  private white = new THREE.Color(1, 1, 1);
  private m = new THREE.Matrix4();
  private q = new THREE.Quaternion();
  private v = new THREE.Vector3();
  private s = new THREE.Vector3();
  private up = new THREE.Vector3(0, 1, 0);
  /** The scene last drawn, and until when (frame clock, s) a flash or a drop still moves; -1: all at rest. */
  private seen: GrandAlleeScene | null = null;
  private busy = -1;

  constructor(scene: THREE.Scene, private board: Board) {
    const pos: number[] = [], idx: number[] = [];
    this.ranges = board.plots.map((p): [number, number] => {
      const inner = inset(p.land, FRAME.width), first = pos.length / 3, n = p.land.length;
      p.land.forEach((o, i) => pos.push(o.x, FRAME.hidden, o.z, inner[i].x, FRAME.hidden, inner[i].z));
      for (let i = 0; i < n; i++) {
        const a = first + 2 * i, b = first + 2 * ((i + 1) % n);
        idx.push(a, b, b + 1, a, b + 1, a + 1);
      }
      return [first, 2 * n];
    });
    this.pos = new Float32Array(pos);
    this.col = new Float32Array(pos.length).fill(1);
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.BufferAttribute(this.pos, 3).setUsage(THREE.DynamicDrawUsage));
    g.setAttribute("color", new THREE.BufferAttribute(this.col, 3).setUsage(THREE.DynamicDrawUsage));
    g.setIndex(idx);
    this.frames = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ vertexColors: true, side: THREE.DoubleSide }));
    this.frames.frustumCulled = false;
    this.gates = new THREE.InstancedMesh(gateGeometry(), new THREE.MeshBasicMaterial({ vertexColors: true }), board.fields.length);
    this.gates.setColorAt(0, this.c.set(GROUND.free)); // the colour buffer exists from the start, so the program is built with it
    this.gates.count = 0;
    this.gates.frustumCulled = false;
    scene.add(this.frames, this.gates);
  }

  /**
   * `pulses` by hotel and `drops` by field: when (frame clock, s) that plot changed hands or that entrance was placed. Redraws only
   * for a new scene or while a flash or a drop moves (a player's colour changing mid-game shows with the next scene).
   */
  update(scene: GrandAlleeScene, colorOf: (player: number) => string, pulses: Map<number, number>, drops: Map<number, number>, t: number) {
    if (scene === this.seen && this.busy < 0) return;
    this.seen = scene;
    let end = -1;
    const owner = new Map(scene.estates.map((e) => [e.hotel, e.owner]));
    const paint = (who: number | undefined) => this.c.set(who === undefined ? GROUND.free : colorOf(who));
    this.board.plots.forEach((p, i) => {
      const who = owner.get(p.hotel), since = pulses.get(p.hotel), a = since === undefined ? 0 : t - since, [first, count] = this.ranges[i];
      const flash = since === undefined ? 0 : Math.max(0, 1 - a / TIMES.pulse) * (0.5 + 0.5 * Math.cos(a * 9));
      if (since !== undefined) end = Math.max(end, since + TIMES.pulse);
      paint(who).lerp(this.white, flash * 0.7);
      for (let j = first; j < first + count; j++) {
        this.pos[j * 3 + 1] = who === undefined ? FRAME.hidden : FRAME.y;
        [this.col[j * 3], this.col[j * 3 + 1], this.col[j * 3 + 2]] = [this.c.r, this.c.g, this.c.b];
      }
    });
    this.frames.geometry.attributes.position.needsUpdate = true;
    this.frames.geometry.attributes.color.needsUpdate = true;

    let n = 0;
    for (const e of scene.entrances) {
      const f = this.board.fields[e.field], plot = this.board.plots[e.hotel];
      if (!f || !plot) continue;
      // the walk's right is three's local +z of the field, turned along the curve at its centre; the gate stands on the plot's side
      const rx = Math.sin(f.rot) * plot.side, rz = Math.cos(f.rot) * plot.side;
      const since = drops.get(e.field), v = since === undefined ? 1 : Math.min(1, (t - since) / TIMES.place), ease = 1 - (1 - v) ** 3;
      if (since !== undefined) end = Math.max(end, since + TIMES.place);
      this.v.set(f.x + rx * GATE_EDGE, (1 - ease) * 9, f.z + rz * GATE_EDGE);
      this.gates.setMatrixAt(n, this.m.compose(this.v, this.q.setFromAxisAngle(this.up, f.rot), this.s.setScalar(0.4 + 0.6 * ease)));
      this.gates.setColorAt(n++, paint(owner.get(e.hotel)));
    }
    this.gates.count = n;
    this.gates.instanceMatrix.needsUpdate = true;
    if (this.gates.instanceColor) this.gates.instanceColor.needsUpdate = true;
    // one more pass once everything has come to rest, so the last frame drawn shows the final colours and places
    this.busy = end > t ? end : -1;
  }
}
