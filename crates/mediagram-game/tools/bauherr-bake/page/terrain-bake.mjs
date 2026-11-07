// Bakes the terrain: for every patch of terrain-field.mjs a height grid, then per texel the normal, the sun (marched
// along the grid, so ridges shade the gullies and cast shadows over the lawn), the sky occlusion and the colour (real rock,
// scree, meadow and snow photos laid on by slope and height, triplanar), multiplied into one lit image. The mesh follows the
// same heights, so the TV draws it unlit with one texture, as it does the town.
import { TEX, over } from './paint.mjs';
import { look } from './look.mjs';
import { canvas } from './util.mjs';
import { noise, fbm, smooth } from './noise.mjs';
import { PATCHES, ATLAS } from './terrain-field.mjs';
import { inWater } from './scenery-map.mjs';
import { LIGHT_MAX } from './export.mjs';

const WATER = [63, 123, 130];
// a photo as pixels at 512 px (the 1k photo scaled down, so sampling at far smaller scales does not alias)
const pixels = (src) => { const c = canvas(512, 512), g = c.getContext('2d', { willReadFrequently: true }); g.drawImage(typeof src === 'string' ? TEX[src] : src, 0, 0, 512, 512); return g.getImageData(0, 0, 512, 512).data; };
// rock in daylight is grey: take part of the photo's warmth out
const grey = (c, k) => { const l = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]; for (let q = 0; q < 3; q++) c[q] = l + (c[q] - l) * k; };
// triplanar sample: the photo is laid from the three axes, weighted by the normal; `s` photo repeats per world unit
function tri(d, x, y, z, nx, ny, nz, s, out) {
  const wx = nx ** 4, wy = ny ** 4, wz = nz ** 4, t = wx + wy + wz;
  out[0] = out[1] = out[2] = 0;
  for (const [u, v, w] of [[z, y, wx], [x, z, wy], [x, y, wz]]) {
    if (w < 1e-3) continue;
    const i = ((((Math.floor(v * s * 512) % 512) + 512) % 512) * 512 + (((Math.floor(u * s * 512) % 512) + 512) % 512)) * 4, k = w / t;
    out[0] += d[i] * k; out[1] += d[i + 1] * k; out[2] += d[i + 2] * k;
  }
}
const mix = (a, b, k) => { a[0] += (b[0] - a[0]) * k; a[1] += (b[1] - a[1]) * k; a[2] += (b[2] - a[2]) * k; };
const lin = new Float32Array(256).map((_, i) => (i / 255) ** 2.2);

/**
 * Returns { canvas, mesh: { pos, uv, col, idx }, shadows } for every patch, packed into one atlas. `shadows` is what the terrain
 * casts on the flat ground round it ({ x0, z0, x1, z1, w, h, data } with data 0 lit .. 1 shadow), for the ground print to take in.
 */
export function bakeTerrain(light) {
  const T = Object.fromEntries(['rock_face_03', 'rocky_terrain', 'snow_02', 'seaside_rock', 'grass_ground'].map((n) => [n, pixels(n)]));
  T.sparse_grass = pixels(over('sparse_grass', look.grass, 'soft-light', 2));   // the very lawn the ground print is laid with
  const atlas = canvas(...ATLAS), ag = atlas.getContext('2d');
  const pos = [], uv = [], idx = [], shadows = [];
  const rock = [0, 0, 0], scree = [0, 0, 0], snow = [0, 0, 0], grass = [0, 0, 0], lawn = [0, 0, 0], c = [0, 0, 0];
  for (const P of PATCHES) {
    const W = Math.round((P.x1 - P.x0) * P.ppu), Hh = Math.round((P.z1 - P.z0) * P.ppu), k = 1 / P.ppu;
    const H = new Float32Array(W * Hh), at = (px, pz) => H[Math.min(Hh - 1, Math.max(0, pz)) * W + Math.min(W - 1, Math.max(0, px))];
    for (let j = 0; j < Hh; j++) for (let i = 0; i < W; i++) H[j * W + i] = P.H(P.x0 + (i + 0.5) * k, P.z0 + (j + 0.5) * k);
    const img = new ImageData(W, Hh), shadow = new Float32Array(W * Hh), L = light.L, hl = Math.hypot(L.x, L.z), sx = L.x / hl, sz = L.z / hl, tanE = L.y / hl;
    for (let j = 0; j < Hh; j++) {
      for (let i = 0; i < W; i++) {
        const h = H[j * W + i], x = P.x0 + (i + 0.5) * k, z = P.z0 + (j + 0.5) * k;
        // the normal from the heights beside it (z grows with the row)
        let nx = (at(i - 1, j) - at(i + 1, j)) * P.ppu * 0.5, nz = (at(i, j - 1) - at(i, j + 1)) * P.ppu * 0.5;
        const nl = Math.hypot(nx, 1, nz); nx /= nl; nz /= nl; const ny = 1 / nl, slope = 1 - ny;
        // the sun: march towards it; how far the ground ahead stands above the ray is how deep in shadow the texel is
        let sh = 0;
        for (let t = 0.25; t < 40; t += 0.25 * (1 + t * 0.12)) {
          const px = i + Math.round(sx * t * P.ppu), pz = j + Math.round(sz * t * P.ppu);
          if (px < 0 || pz < 0 || px >= W || pz >= Hh) break;
          sh = Math.max(sh, Math.min(1, (H[pz * W + px] - (h + t * tanE)) / (0.12 + 0.07 * t)));
        }
        shadow[j * W + i] = sh;
        // sky occlusion: the highest ground around, in eight directions
        let occ = 0;
        for (let a = 0; a < 8; a++) {
          let m = 0;
          for (const d of [0.3, 0.7, 1.4]) {
            const hh = at(i + Math.round(Math.cos(a * 0.785) * d * P.ppu), j + Math.round(Math.sin(a * 0.785) * d * P.ppu));
            m = Math.max(m, (hh - h) / d);
          }
          occ += Math.min(1, m / 1.1);
        }
        const ao = 1 - 0.55 * (occ / 8);
        // the colour
        const alt = h / 9, n1 = fbm(x * 0.8, z * 0.8, 3), n2 = noise(x * 3.1 + 40, z * 3.1);
        tri(T.sparse_grass, x, h, z, 0, 1, 0, 1 / 1.6, lawn);
        if (P.kind === 'alpine') {
          tri(T.rock_face_03, x, h, z, nx, ny, nz, 0.22, rock); grey(rock, 0.45); rock[0] *= 0.92; rock[1] *= 0.92; rock[2] *= 0.92;
          tri(T.rocky_terrain, x, h, z, nx, ny, nz, 0.32, scree); grey(scree, 0.6); scree[0] *= 0.9; scree[1] *= 0.9; scree[2] *= 0.85;
          tri(T.snow_02, x, h, z, 0, 1, 0, 0.25, snow); { const l = 0.3 * snow[0] + 0.59 * snow[1] + 0.11 * snow[2], s = 238 + (l - 170) * 0.2; snow[0] = s - 3; snow[1] = s; snow[2] = s + 6; }
          tri(T.grass_ground, x, h, z, 0, 1, 0, 0.35, grass); grass[0] *= 0.9; grass[1] *= 1.05; grass[2] *= 0.72;
          c[0] = scree[0]; c[1] = scree[1]; c[2] = scree[2];
          mix(c, rock, smooth(0.22, 0.5, slope + 0.12 * n1));
          mix(c, lawn, (1 - smooth(0.0, 0.5, h + 0.5 * n1)) * (1 - smooth(0.12, 0.4, slope)));
          mix(c, grass, (1 - smooth(0.3, 1.9, h + 0.7 * n1)) * (1 - smooth(0.18, 0.42, slope)) * 0.85);
          mix(c, snow, smooth(0.5, 0.64, alt + 0.1 * n1 + 0.03 * n2) * (1 - smooth(0.34, 0.62, slope + 0.1 * n2)));
        } else if (P.kind === 'meadow') {
          tri(T.grass_ground, x, h, z, 0, 1, 0, 0.5, grass); grass[0] *= 0.95; grass[1] *= 1.08; grass[2] *= 0.75;
          c[0] = grass[0]; c[1] = grass[1]; c[2] = grass[2];
          mix(c, lawn, 1 - smooth(0.0, 0.35, h));
        } else {
          tri(T.seaside_rock, x, h, z, nx, ny, nz, 0.7, scree); grey(scree, 0.5); scree[0] *= 1.45; scree[1] *= 1.45; scree[2] *= 1.45;
          tri(T.rock_face_03, x, h, z, nx, ny, nz, 0.5, rock); grey(rock, 0.4); rock[0] *= 1.0; rock[1] *= 1.0; rock[2] *= 1.0;
          c[0] = scree[0]; c[1] = scree[1]; c[2] = scree[2];
          mix(c, rock, smooth(0.04, 0.3, h + 0.08 * n2));
          mix(c, [150, 160, 90], 0.45 * smooth(0.55, 0.9, n2 + 0.4 * ny));   // lichen on the sunward tops
          mix(c, [60, 70, 70], 0.55 * (1 - smooth(0.0, 0.1, h)));            // wet at the waterline
          if (h < 0.02) { c[0] = WATER[0]; c[1] = WATER[1]; c[2] = WATER[2]; if (!inWater(x, z)) { c[0] = lawn[0]; c[1] = lawn[1]; c[2] = lawn[2]; } }
        }
        const e = light.at(nx, ny, nz, 1 - sh, ao), o = (j * W + i) * 4;
        for (let q = 0; q < 3; q++) img.data[o + q] = Math.min(255, Math.round(255 * Math.pow(Math.min(1, (lin[Math.min(255, Math.max(0, Math.round(c[q])))] * e[q]) / LIGHT_MAX), 1 / 2.2)));
        img.data[o + 3] = 255;
      }
    }
    ag.putImageData(img, P.ax, P.ay);
    shadows.push({ x0: P.x0, z0: P.z0, x1: P.x1, z1: P.z1, w: W, h: Hh, data: shadow });
    // the mesh: a grid on the heights, without the flat quads (the lawn and water prints show there)
    const nx = Math.round((P.x1 - P.x0) / P.step), nz = Math.round((P.z1 - P.z0) / P.step), base = pos.length / 3;
    const vx = (i) => P.x0 + (i / nx) * (P.x1 - P.x0), vz = (j) => P.z0 + (j / nz) * (P.z1 - P.z0);
    const hv = (i, j) => P.H(vx(i), vz(j));
    const grid = Array.from({ length: nz + 1 }, (_, j) => Array.from({ length: nx + 1 }, (_, i) => hv(i, j)));
    for (let j = 0; j <= nz; j++) for (let i = 0; i <= nx; i++) {
      pos.push(vx(i), Math.max(0.012, grid[j][i]), vz(j));
      uv.push((P.ax + (i / nx) * (W - 1) + 0.5) / ATLAS[0], 1 - (P.ay + (j / nz) * (Hh - 1) + 0.5) / ATLAS[1]);
    }
    for (let j = 0; j < nz; j++) for (let i = 0; i < nx; i++) {
      const hs = [grid[j][i], grid[j][i + 1], grid[j + 1][i], grid[j + 1][i + 1]];
      if (Math.max(...hs) < 0.015) continue;
      const a = base + j * (nx + 1) + i, b = a + 1, d = a + nx + 1, e = d + 1;
      idx.push(a, d, b, b, d, e);   // counter-clockwise seen from above
    }
  }
  return { canvas: atlas, shadows, mesh: { pos: new Float32Array(pos), uv: new Float32Array(uv), col: new Float32Array(pos.length).fill(LIGHT_MAX), idx: Uint32Array.from(idx) } };
}
