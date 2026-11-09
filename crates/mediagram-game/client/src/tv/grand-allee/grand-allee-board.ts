// The board under everything. Once 4b's board.glb exists it is used as baked; until then a stand-in is drawn from the layout in
// one mesh: grass, sea, river, pond, the road and its 40 curved fields coloured by kind. The table beneath and the board's edge make it a
// board game on a table. All of it is unlit flat colour: large flat surfaces lit per pixel cost the TV box.

import * as THREE from "three";
import type { GrandAlleeFieldKind } from "../../protocol/GrandAlleeFieldKind";
import { toSpot, type Board, type Spot } from "./grand-allee-layout";
import { GROUND, KIND_COLORS, SUN } from "./grand-allee-look";

type P3 = [number, number, number];
const LIGHT = new THREE.Vector3(...SUN).normalize();
const THICK = 1.6;

/** Bakes a simple light into a small piece's vertex colours from its normals: the scene has no lights, unlit is what the box draws fast. */
export function shade<T extends THREE.BufferGeometry>(geo: T, floor = 0.55): T {
  const n = geo.getAttribute("normal"), c = new Float32Array(n.count * 3), v = new THREE.Vector3();
  for (let i = 0; i < n.count; i++) {
    v.fromBufferAttribute(n, i);
    c.fill(Math.min(1, floor + (1 - floor) * Math.max(0, v.dot(LIGHT)) + 0.15 * Math.max(0, v.y)), i * 3, i * 3 + 3);
  }
  geo.setAttribute("color", new THREE.BufferAttribute(c, 3));
  return geo;
}

/** Flat colour polygons gathered into one mesh. */
class Paint {
  private pos: number[] = [];
  private col: number[] = [];
  private idx: number[] = [];
  private c = new THREE.Color();

  /** A convex polygon, as a fan. */
  face(pts: P3[], css: string) {
    const b = this.pos.length / 3;
    this.c.set(css);
    for (const p of pts) {
      this.pos.push(...p);
      this.col.push(this.c.r, this.c.g, this.c.b);
    }
    for (let i = 1; i < pts.length - 1; i++) this.idx.push(b, b + i, b + i + 1);
  }
  flat(pts: Spot[], y: number, css: string) {
    this.face(pts.map((s): P3 => [s.x, y, s.z]), css);
  }
  disc(o: Spot, r: number, y: number, css: string, seg = 28) {
    const a = (i: number) => (i / seg) * 2 * Math.PI;
    this.flat(Array.from({ length: seg }, (_, i) => ({ x: o.x + r * Math.cos(a(i)), z: o.z + r * Math.sin(a(i)) })), y, css);
  }
  /** A ribbon along a line, with round joints. */
  ribbon(line: Spot[], width: number, y: number, css: string) {
    const h = width / 2;
    line.slice(1).forEach((b, i) => {
      const a = line[i], l = Math.hypot(b.x - a.x, b.z - a.z) || 1, nx = (-(b.z - a.z) / l) * h, nz = ((b.x - a.x) / l) * h;
      this.flat([{ x: a.x + nx, z: a.z + nz }, { x: a.x - nx, z: a.z - nz }, { x: b.x - nx, z: b.z - nz }, { x: b.x + nx, z: b.z + nz }], y, css);
    });
    line.slice(1, -1).forEach((p) => this.disc(p, h, y, css, 16));
  }
  mesh() {
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.Float32BufferAttribute(this.pos, 3));
    g.setAttribute("color", new THREE.Float32BufferAttribute(this.col, 3));
    g.setIndex(this.idx);
    return new THREE.Mesh(g, new THREE.MeshBasicMaterial({ vertexColors: true, side: THREE.DoubleSide }));
  }
}

const rect = (x0: number, z0: number, x1: number, z1: number): Spot[] => [{ x: x0, z: z0 }, { x: x1, z: z0 }, { x: x1, z: z1 }, { x: x0, z: z1 }];

/** Lays the board (4b's model, or the stand-in) on its table; `kinds`: the field kinds by id, as the host sends them. */
export function buildBoard(scene: THREE.Scene, board: Board, kinds: GrandAlleeFieldKind[], model: THREE.Object3D | null) {
  const p = new Paint(), w = board.width / 2, d = board.depth / 2, j = board.json;
  p.flat(rect(-400, -400, 400, 400), -THICK, GROUND.table);
  // the board's edge: the south side faces the TV, east and west show a sliver, the north is never seen
  p.face([[-w, 0, d], [w, 0, d], [w, -THICK, d], [-w, -THICK, d]], GROUND.edge);
  for (const x of [-w, w]) p.face([[x, 0, -d], [x, 0, d], [x, -THICK, d], [x, -THICK, -d]], GROUND.side);
  if (model) scene.add(model);
  else {
    // layers 2 cm apart: enough for the depth buffer at the camera's distance, too little to see
    p.flat(rect(-w, -d, w, d), 0, GROUND.grass);
    p.flat(rect(-w, -d, w, -j.sea_y), 0.03, GROUND.water);
    p.ribbon(j.river.map((q) => toSpot(q)), j.river_width, 0.05, GROUND.water);
    p.disc(toSpot(j.pond), j.pond[2], 0.07, GROUND.water, 40);
    board.fields.forEach((f, i) => {
      // the field along the curve, quad by quad between its two edges, and its inlay a little in from the edges and the ends
      const n = f.mid.length, left = f.outline.slice(0, n), right = f.outline.slice(n).reverse();
      const along = (s: Spot[], k: number) => {
        const j = Math.min(Math.floor(k), s.length - 2), g = k - j;
        return { x: s[j].x + (s[j + 1].x - s[j].x) * g, z: s[j].z + (s[j + 1].z - s[j].z) * g };
      };
      const inlay = (s: Spot[], k: number) => {
        const e = along(s, k), m = along(f.mid, k);
        return { x: m.x + (e.x - m.x) * 0.86, z: m.z + (e.z - m.z) * 0.86 };
      };
      const ks = [0.5, ...Array.from({ length: n - 2 }, (_, k) => k + 1), n - 1.5];
      for (let k = 0; k + 1 < n; k++) p.flat([left[k], left[k + 1], right[k + 1], right[k]], 0.09, GROUND.road);
      for (let j = 0; j + 1 < ks.length; j++) {
        p.flat([inlay(left, ks[j]), inlay(left, ks[j + 1]), inlay(right, ks[j + 1]), inlay(right, ks[j])], 0.11, KIND_COLORS[kinds[i] ?? "allee"]);
      }
    });
  }
  scene.add(p.mesh());
}
