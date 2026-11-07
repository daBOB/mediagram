// The shapes of the land that rises above the lawn, as height fields: the snow range behind the Alpenrose, the
// hill behind the Sternwarte and the boulders along the Leuchtturm's coast. terrain-bake.mjs lights and paints them.
import { fbm, ridged, smooth } from './noise.mjs';

const hash = (i, k) => { const s = Math.sin(i * 127.1 + k * 311.7) * 43758.5453; return s - Math.floor(s); };
const bump = (x, z, cx, cz, r, h, p) => { const d = ((x - cx) ** 2 + (z - cz) ** 2) / (r * r); return d >= 1 ? 0 : h * (1 - d) ** p; };

// [x, z, radius, height]: tall peaks and the foothills in front of them
const PEAKS = [[17, -19, 7.6, 8.4], [12.2, -21.4, 5.6, 6.0], [24, -20.5, 8.4, 10.2], [30.5, -16.5, 6, 5.6], [33, -21, 6.5, 7.2], [14.8, -15.2, 4.4, 2.5], [20.2, -14.4, 4.2, 2.2], [26.5, -13.6, 3.8, 1.8]];
function range(x, z) {
  let top = 0, sum = 0;
  for (const [cx, cz, r, h] of PEAKS) { const b = bump(x, z, cx, cz, r, h, 1.45); top = Math.max(top, b); sum += b; }
  const h0 = top * 0.72 + sum * 0.28;
  if (h0 <= 0) return 0;
  // ridges and gullies scale with the mass, so the foot stays on the lawn
  return h0 * (0.7 + 0.42 * ridged(x / 4.2, z / 4.2, 4)) + 0.2 * fbm(x * 1.4, z * 1.4, 3) * smooth(0.3, 2.5, h0);
}
function hill(x, z) {
  const b = bump(x, z, 11.2, -3.0, 2.8, 1.5, 1.1);
  return b > 0 ? b * (1 + 0.22 * fbm(x * 0.9, z * 0.9, 3)) + 0.05 * fbm(x * 3, z * 3, 2) * smooth(0, 0.4, b) : 0;
}

// boulders: [x, z, radius along, radius across, turn, height]. Along the shore, then a few out in the surf.
const SHORE = [[-18, 6.3], [-14, 6.1], [-9.5, 6.9], [-6.6, 8.6], [-5, 11], [-4.7, 13]];
const BOULDERS = [];
{
  let n = 0;
  for (let i = 0; i + 1 < SHORE.length; i++) {
    const [ax, az] = SHORE[i], [bx, bz] = SHORE[i + 1], len = Math.hypot(bx - ax, bz - az);
    for (let t = 0; t < len; t += 0.8) {
      const f = t / len, side = (hash(n, 1) - 0.5) * 0.7, nx = -(bz - az) / len, nz = (bx - ax) / len, r = 0.3 + hash(n, 2) * 0.4;
      BOULDERS.push([ax + (bx - ax) * f + nx * side, az + (bz - az) * f + nz * side, r * 1.15, r, hash(n, 3) * 3, r * (0.55 + hash(n, 4) * 0.5)]);
      n++;
    }
  }
  for (const [x, z, r] of [[-6.4, 8.9, 0.6], [-5.4, 10.8, 0.5], [-8.2, 7.8, 0.45], [-11.5, 7.5, 0.42], [-4.9, 12.4, 0.55]]) BOULDERS.push([x, z, r * 1.2, r, hash(x, 5) * 3, r * 0.9]);
}
function coast(x, z) {
  let h = 0;
  for (const [cx, cz, rx, rz, a, bh] of BOULDERS) {
    const dx = x - cx, dz = z - cz;
    if (dx * dx + dz * dz > (rx + rz) ** 2) continue;
    const u = (dx * Math.cos(a) + dz * Math.sin(a)) / rx, v = (-dx * Math.sin(a) + dz * Math.cos(a)) / rz, e = u * u + v * v;
    if (e < 1) h = Math.max(h, bh * Math.sqrt(1 - e));
  }
  return h > 0 ? h * (0.8 + 0.4 * ridged(x * 2.4, z * 2.4, 3)) + 0.03 * fbm(x * 8, z * 8, 2) * smooth(0, 0.1, h) : 0;
}

/**
 * Patches, in world units: the area, texels per unit, mesh spacing, the height, the paint rule, and where the patch
 * sits in the terrain atlas (pixels).
 */
export const PATCHES = [
  { name: 'range', kind: 'alpine', x0: 3, x1: 35, z0: -25, z1: -9, ppu: 44, step: 0.25, H: range, ax: 0, ay: 0 },
  { name: 'hill', kind: 'meadow', x0: 7.8, x1: 14.8, z0: -6, z1: -0.1, ppu: 44, step: 0.25, H: hill, ax: 1424, ay: 0 },
  { name: 'coast', kind: 'rocks', x0: -19, x1: -3.5, z0: 5, z1: 14.4, ppu: 80, step: 0.12, H: coast, ax: 0, ay: 720 },
];
export const ATLAS = [2048, 1472];
