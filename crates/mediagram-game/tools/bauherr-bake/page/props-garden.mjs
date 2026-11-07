// Trees, hedges, lamps and the small things in a garden, built into a Part (see atlas.mjs). Part-local units.
import * as THREE from 'three';
import { mergeVertices } from 'three/addons/utils/BufferGeometryUtils.js';
import { rand } from './util.mjs';
import { glow, photo, over } from './paint.mjs';

// ---------- trees, hedges, lamps, garden things ----------
// a broadleaf crown: a few lumpy balls, leaves photo mapped across them, darker inside and below
export function crownGeo(seed, s = 1, shape = 'round') {
  const parts = [], n = shape === 'tall' ? 4 : 5;
  for (let k = 0; k < n; k++) {
    const a = k * 2.4 + seed, r = (0.2 + rand() * 0.08) * s, up = shape === 'tall' ? 0.45 + k * 0.17 : 0.55 + (k % 2) * 0.12;
    const g = new THREE.IcosahedronGeometry(r, 1), p = g.attributes.position;
    g.deleteAttribute('uv'); g.deleteAttribute('normal');   // so mergeVertices welds by position alone
    for (let i = 0; i < p.count; i++) { const f = 1 + 0.16 * Math.sin(p.getX(i) * 31 + seed) * Math.cos(p.getZ(i) * 27 + p.getY(i) * 19); p.setXYZ(i, p.getX(i) * f, p.getY(i) * f * 0.92, p.getZ(i) * f); }
    const off = k === 0 ? [0, 0] : [Math.cos(a) * 0.13 * s, Math.sin(a) * 0.13 * s];
    g.translate(off[0], up * s, off[1]); parts.push(g);
  }
  return parts;
}
export function tree(P, x, z, s, leaves, kind = 'round') {
  P.cyl(x, z, 0.03 * s, 0.018 * s, 0, 0.5 * s, 6, '#4b3a2c');
  const top = kind === 'tall' ? 1.25 * s : 0.95 * s;
  for (const g of crownGeo(x * 7 + z, s, kind)) {
    const ng = mergeVertices(g); ng.computeVertexNormals(); ng.translate(x, 0, z);
    const P2 = ng.attributes.position, N = ng.attributes.normal, base = P.verts.length;
    for (let i = 0; i < P2.count; i++) {
      const q = new THREE.Vector3().fromBufferAttribute(P2, i), n = new THREE.Vector3().fromBufferAttribute(N, i), h = THREE.MathUtils.clamp((q.y - 0.35 * s) / (top - 0.35 * s), 0, 1);
      P.verts.push({ q, n, r: leaves, s: ((q.x - x) / s + 0.5) * 0.9 + 0.05, t: 1 - h, ao: 0.45 + 0.55 * h, sun: 1 });
    }
    for (let i = 0; i < ng.index.count; i++) P.idx.push(base + ng.index.getX(i));
  }
}
export function conifer(P, x, z, s, leaves) {
  P.cyl(x, z, 0.025 * s, 0.015 * s, 0, 0.25 * s, 6, '#4b3a2c');
  for (let k = 0; k < 4; k++) {
    const y0 = (0.15 + k * 0.22) * s, r = (0.26 - k * 0.055) * s, base = P.verts.length, seg = 9;
    for (let i = 0; i <= seg; i++) {
      const a = (i / seg) * Math.PI * 2, n = new THREE.Vector3(Math.sin(a), 0.6, Math.cos(a)).normalize(), jag = 1 + 0.12 * Math.sin(a * 5 + k);
      P.verts.push({ q: new THREE.Vector3(x + Math.sin(a) * r * jag, y0, z + Math.cos(a) * r * jag), n, r: leaves, s: i / seg, t: 0.95, ao: 0.5, sun: 1 });
      P.verts.push({ q: new THREE.Vector3(x, y0 + 0.42 * s, z), n, r: leaves, s: i / seg, t: 0.05, ao: 1, sun: 1 });
      if (i < seg) { const j = base + i * 2; P.idx.push(j, j + 2, j + 1); }
    }
  }
}
// foliage: the leaf-litter photo laid soft-light over the foliage colour, so it keeps a real texture at any green
export const leavesPaint = (css) => (g, wu, hu) => photo(g, over('forest_leaves_02', css, 'soft-light', 2), -0.1, -0.1, wu + 0.2, hu + 0.2, 0.35);
export const hedge = (P, x0, z0, x1, z1, h, tintCss) => P.box(x0, 0, z0, x1, h, z1, leavesPaint(tintCss), { ao: 0.9 });
export function lamp(P, x, z, lit) {   // a cast-iron street lamp with a four-sided lantern
  P.cyl(x, z, 0.014, 0.009, 0, 0.5, 8, '#262a2c'); P.cyl(x, z, 0.024, 0.024, 0, 0.05, 8, '#262a2c');
  const L = (g, wu, hu) => { g.fillStyle = '#262a2c'; g.fillRect(-1, -1, wu + 2, hu + 2); g.fillStyle = lit ? '#ffe2a8' : '#c9c2b0'; g.fillRect(wu * 0.15, hu * 0.12, wu * 0.7, hu * 0.76); if (lit) glow(g, () => g.fillRect(wu * 0.15, hu * 0.12, wu * 0.7, hu * 0.76), 0.8); };
  const s = 0.03, y0 = 0.5, y1 = 0.58;
  P.wall(x - s, z + s, x + s, z + s, y0, y1, L); P.wall(x + s, z + s, x + s, z - s, y0, y1, L); P.wall(x - s, z - s, x - s, z + s, y0, y1, L);
  P.cyl(x, z, s * 1.5, 0.004, y1, y1 + 0.04, 8, '#262a2c');
}
export function parasol(P, x, z, color, r = 0.09) { P.cyl(x, z, 0.004, 0.004, 0, 0.13, 5, '#d6d0c2'); P.cyl(x, z, r, 0.006, 0.12, 0.155, 10, color); }
export function lounger(P, x, z, rot = 0) {
  const c = Math.cos(rot), s = Math.sin(rot), w = 0.025, l = 0.075;
  const pts = (dx, dz) => [x + dx * c + dz * s, z - dx * s + dz * c];
  const [ax, az] = pts(-w, -l), [bx, bz] = pts(w, l);
  P.box(Math.min(ax, bx), 0.008, Math.min(az, bz), Math.max(ax, bx), 0.02, Math.max(az, bz), '#f1ede4');
}
// a slab or ledge, corners in any order; front painted if given
export function box4(P, x0, z0, x1, z1, y0, y1, css, front) { P.box(Math.min(x0, x1), y0, Math.min(z0, z1), Math.max(x0, x1), y1, Math.max(z0, z1), css, { front, faces: 'px nx pz py' }); }
export const bench = (P, x, z, w = 0.1) => { P.box(x - w / 2, 0.02, z - 0.012, x + w / 2, 0.028, z + 0.012, '#7a5638'); P.box(x - w / 2, 0.028, z - 0.014, x + w / 2, 0.05, z - 0.009, '#7a5638'); };
// a person as in an architect's model: clothes up to the shoulders, a head
export function figure(P, x, z, cloth, skin) {   // 1.75 m at 13 m to the unit
  P.cyl(x, z, 0.016, 0.013, 0, 0.108, 6, cloth); P.cyl(x, z, 0.011, 0.011, 0.108, 0.133, 6, skin, { cap: skin });
}
