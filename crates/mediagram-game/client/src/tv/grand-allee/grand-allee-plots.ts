// Ownership on the ground: a frame round every owned plot in its owner's colour (an unbuilt plot shows its owner too; a free one
// shows nothing over the board's own edging), flashing when the plot changes hands, and the entrances: a small gate on the field's
// road edge on the hotel's side, in the owner's colour, dropping in when placed. One draw call each.

import * as THREE from "three";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { shade } from "./grand-allee-board";
import type { Board } from "./grand-allee-layout";
import { GROUND, TIMES } from "./grand-allee-look";

/** Metres: the frame's distance round the plot and its width; a free plot's frame waits under the board. */
const FRAME = { margin: 0.9, width: 0.7, y: 0.14, hidden: -4 };
/** Metres from the road's centre line to a gate: on the field, at its edge. */
const GATE_EDGE = 2.3;

function gateGeometry() {
  const box = (w: number, h: number, d: number, x: number, y: number) => new THREE.BoxGeometry(w, h, d).translate(x, y, 0);
  const merged = mergeGeometries([box(3.4, 0.16, 1.3, 0, 0.08), box(0.3, 2.8, 0.3, -1.5, 1.4), box(0.3, 2.8, 0.3, 1.5, 1.4), box(3.8, 0.45, 1.5, 0, 2.95)]);
  if (!merged) throw new Error("gate geometry");
  return shade(merged);
}

export class Grounds {
  readonly frames: THREE.Mesh;
  readonly gates: THREE.InstancedMesh;
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
    // per plot four strips round its rectangle (plots stand square to the board): 16 vertices
    const idx: number[] = [];
    this.pos = new Float32Array(board.plots.length * 48);
    this.col = new Float32Array(board.plots.length * 48).fill(1);
    board.plots.forEach((p, i) => {
      const k = FRAME.width, x0 = p.x - p.w / 2 - FRAME.margin, x1 = p.x + p.w / 2 + FRAME.margin, z0 = p.z - p.d / 2 - FRAME.margin, z1 = p.z + p.d / 2 + FRAME.margin;
      [[x0, z0, x1, z0 + k], [x0, z1 - k, x1, z1], [x0, z0 + k, x0 + k, z1 - k], [x1 - k, z0 + k, x1, z1 - k]].forEach(([a, b, c, d], strip) => {
        const base = i * 16 + strip * 4;
        [[a, b], [c, b], [c, d], [a, d]].forEach(([x, z], j) => this.pos.set([x, FRAME.hidden, z], (base + j) * 3));
        idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
      });
    });
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
      const who = owner.get(p.hotel), since = pulses.get(p.hotel), a = since === undefined ? 0 : t - since;
      const flash = since === undefined ? 0 : Math.max(0, 1 - a / TIMES.pulse) * (0.5 + 0.5 * Math.cos(a * 9));
      if (since !== undefined) end = Math.max(end, since + TIMES.pulse);
      paint(who).lerp(this.white, flash * 0.7);
      for (let j = i * 16; j < i * 16 + 16; j++) {
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
      // the walk's right is three's local +z of the field; the gate stands on the side the hotel is on
      const rx = Math.sin(f.rot), rz = Math.cos(f.rot), side = (plot.x - f.x) * rx + (plot.z - f.z) * rz >= 0 ? 1 : -1;
      const since = drops.get(e.field), v = since === undefined ? 1 : Math.min(1, (t - since) / TIMES.place), ease = 1 - (1 - v) ** 3;
      if (since !== undefined) end = Math.max(end, since + TIMES.place);
      this.v.set(f.x + rx * side * GATE_EDGE, (1 - ease) * 9, f.z + rz * side * GATE_EDGE);
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
