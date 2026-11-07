// Trees, lamps and lawn furniture, planted where nothing else stands: not on the road, in a lot, on the plaza or in water.
import * as K from './props.mjs';
import { rand } from './util.mjs';
import { LOTS, PLAZA, LOT_W, LOT_D, ROAD_WIDTH, FIELDS, roadLine } from '../../../client/src/tv/bauherr/bauherr-layout.ts';
import { inWater, ZONES, BRIDGE } from './scenery-map.mjs';

const road = roadLine(0.2);
export const nearRoad = (x, z, m) => road.some((f) => (f.x - x) ** 2 + (f.z - z) ** 2 < (ROAD_WIDTH / 2 + m) ** 2);
const inLot = (x, z, m) => LOTS.some((l) => {
  const dx = x - l.x, dz = z - l.z, c = Math.cos(l.rot), s = Math.sin(l.rot);
  return Math.abs(dx * c - dz * s) < LOT_W / 2 + m && Math.abs(dx * s + dz * c) < LOT_D / 2 + m;
});
const free = (x, z, m = 0.5) => !nearRoad(x, z, 0.9 + m) && !inLot(x, z, m) && !inWater(x, z, 0.7 + m) && !(x > PLAZA.x0 - 1 && x < PLAZA.x1 + 1 && z > PLAZA.z0 - 1) && Math.hypot(x - BRIDGE.x, z - BRIDGE.z) > 2.2;
const zone = (kind) => ZONES.find((z) => z[4] === kind);

/** Plants trees in a rectangle: n tries' worth of `count` trees at least `gap` apart; kind(i) picks 'round', 'tall' or 'fir'. */
function scatter(P, sh, faces, [x0, z0, x1, z1], count, gap, kind, size, placed) {
  let n = 0;
  for (let t = 0; t < 4000 && n < count; t++) {
    const x = x0 + rand() * (x1 - x0), z = z0 + rand() * (z1 - z0), k = kind(n), s = size[0] + rand() * (size[1] - size[0]);
    if (!free(x, z) || placed.some((q) => (q[0] - x) ** 2 + (q[1] - z) ** 2 < gap * gap)) continue;
    placed.push([x, z]); n++;
    if (k === 'fir') { K.conifer(P, x, z, s, faces.firs); sh.trees.push({ x, z, r: 0.26 * s, h: 0.65 * s }); }
    else { K.tree(P, x, z, s, faces.leaves, k); sh.trees.push({ x, z, r: 0.34 * s, h: 0.8 * s }); }
  }
}
export function plant(P, sh, faces) {
  const placed = [], rnd = (a, b) => (n) => (rand() < a ? 'fir' : rand() < b ? 'tall' : 'round');
  const F = zone('forest'), O = zone('orchard'), PK = zone('park'), SH = zone('shore');
  scatter(P, sh, faces, F, 62, 0.85, rnd(0.8, 0.4), [1.2, 1.9], placed);
  scatter(P, sh, faces, [-1.4, -14, 1.2, -12.5], 6, 0.9, rnd(0.6, 0.5), [1.2, 1.6], placed);
  scatter(P, sh, faces, [8.5, -17, 23, -12.8], 26, 1.3, rnd(0.9, 0.3), [1.3, 2.1], placed);
  scatter(P, sh, faces, O, 11, 1.7, () => 'round', [1.2, 1.5], placed);
  scatter(P, sh, faces, PK, 9, 1.9, () => 'round', [1.3, 1.7], placed);
  scatter(P, sh, faces, SH, 9, 1.8, rnd(0.2, 0.8), [1.3, 1.8], placed);
  scatter(P, sh, faces, [8.8, -5.2, 16, -1.5], 8, 1.4, rnd(0.5, 0.5), [1.1, 1.5], placed);
  scatter(P, sh, faces, [-3, 5.3, 10, 7.0], 6, 2.6, () => 'round', [1.3, 1.6], placed);
  scatter(P, sh, faces, [-26, -2, -17, 5], 8, 1.8, rnd(0.2, 0.8), [1.3, 1.8], placed);
}
/** Street lamps along the road, on whichever side is free; returns their glow discs. */
export function lamps(P, sh) {
  const glows = [];
  FIELDS.forEach((f, i) => {
    if (i % 2) return;
    for (const side of [1, -1]) {
      const x = f.x - Math.sin(f.ang) * 1.15 * side, z = f.z + Math.cos(f.ang) * 1.15 * side;
      if (inLot(x, z, 0.15) || inWater(x, z, 0.4) || (x > PLAZA.x0 && x < PLAZA.x1 && z > PLAZA.z0 - 0.3) || Math.hypot(x - BRIDGE.x, z - BRIDGE.z) < 1.8) continue;
      K.lamp(P, x, z, true); sh.lines.push({ x, z, h: 0.6, w: 0.012 }); glows.push([x, 0.55, z, 0.42, 0.22, '#ffcf8a']);
      return;
    }
  });
  return glows;
}
/** The plaza's furniture: lamps at its corners. */
export function plaza(P, sh) {
  const glows = [];
  for (const [x, z] of [[PLAZA.x0 + 0.6, PLAZA.z0 + 0.7], [PLAZA.x1 - 0.6, PLAZA.z0 + 0.7], [PLAZA.x0 + 0.6, PLAZA.z1 - 0.6], [PLAZA.x1 - 0.6, PLAZA.z1 - 0.6]]) {
    K.lamp(P, x, z, true); sh.lines.push({ x, z, h: 0.6, w: 0.012 }); glows.push([x, 0.55, z, 0.42, 0.22, '#ffcf8a']);
  }
  
  return glows;
}
/** The spa park beside the Kurhaus: clipped hedges along a gravel walk, with benches and a lamp. Returns the lamp's glow. */
export function park(P, sh) {
  for (const z of [-4.35, -3.1]) { K.hedge(P, 4.0, z - 0.17, 7.8, z + 0.17, 0.16, '#4d6b3a'); sh.polys.push([[4.0, 0, z - 0.17], [7.8, 0, z - 0.17], [7.8, 0.16, z - 0.17], [4.0, 0.16, z - 0.17]]); }
  for (const x of [5.0, 6.8]) K.bench(P, x, -3.72, 0.2);
  K.lamp(P, 7.9, -3.72, true); sh.lines.push({ x: 7.9, z: -3.72, h: 0.6, w: 0.012 });
  return [[7.9, 0.55, -3.72, 0.42, 0.22, '#ffcf8a']];
}
