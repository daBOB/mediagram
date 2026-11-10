// The full-detail mesh's shading frame at any point of its texture space, so the rebake can apply the 4K normal map
// to the surface it was made for rather than to the simplified one: the folds and edges simplification flattens (a
// hoodie pocket, the rim of an ear) come back as normal-map detail. Simplifying keeps Meshy's texture seams, so every
// point of the simplified mesh has the texture coordinates of the full-mesh point it stands for: no rays needed.
import { forEachUvTexel } from './pad-texture-islands.mjs';
import { frame } from './rebake-texture-atlas.mjs';

const GRID = 4096; // the master maps' size: one cell per texel they hold

/** Copies the full mesh of `prim` (float attributes, before simplifying) and returns `at(u, v, point, nrm, T, B)`:
 *  fills the full mesh's interpolated normal and tangent frame at texture point (u, v). False where no triangle covers
 *  it, or where the full mesh there lies more than 1% of its height from `point` (the simplified surface's point):
 *  simplifying shortens seams, so a simplified triangle can reach into a neighbouring island of another body part. */
export function fullDetailNormalFrames(prim) {
  const p = Float32Array.from(prim.getAttribute('POSITION').getArray());
  const n = Float32Array.from(prim.getAttribute('NORMAL').getArray());
  const uv = Float32Array.from(prim.getAttribute('TEXCOORD_0').getArray());
  const idx = Uint32Array.from(prim.getIndices().getArray());
  let lo = Infinity, hi = -Infinity;
  for (let i = 1; i < p.length; i += 3) (lo = Math.min(lo, p[i])), (hi = Math.max(hi, p[i]));
  const near = (0.01 * (hi - lo)) ** 2;
  const cell = new Int32Array(GRID * GRID).fill(-1);
  forEachUvTexel(uv, idx, GRID, GRID, (i, t) => (cell[i] = t));
  const e1 = [0, 0, 0], e2 = [0, 0, 0], d1 = [0, 0], d2 = [0, 0];
  return (u, v, point, nrm, T, B) => {
    const x = Math.min(GRID - 1, Math.max(0, Math.floor(u * GRID))), y = Math.min(GRID - 1, Math.max(0, Math.floor(v * GRID)));
    const t = cell[y * GRID + x];
    if (t < 0) return false;
    const a = idx[t], b = idx[t + 1], c = idx[t + 2], ax = uv[a * 2], ay = uv[a * 2 + 1];
    (d1[0] = uv[b * 2] - ax), (d1[1] = uv[b * 2 + 1] - ay), (d2[0] = uv[c * 2] - ax), (d2[1] = uv[c * 2 + 1] - ay);
    const det = d1[0] * d2[1] - d1[1] * d2[0];
    if (det === 0) return false;
    // (u, v) = a + wb·d1 + wc·d2; clamped, since (u, v) may sit up to a cell outside the triangle that owns the cell
    let wb = Math.max(0, ((u - ax) * d2[1] - (v - ay) * d2[0]) / det), wc = Math.max(0, (d1[0] * (v - ay) - d1[1] * (u - ax)) / det);
    if (wb + wc > 1) (wb /= wb + wc), (wc = 1 - wb);
    const wa = 1 - wb - wc;
    let d2sum = 0;
    for (let k = 0; k < 3; k++) d2sum += (wa * p[a * 3 + k] + wb * p[b * 3 + k] + wc * p[c * 3 + k] - point[k]) ** 2;
    if (d2sum > near) return false;
    for (let k = 0; k < 3; k++) {
      nrm[k] = wa * n[a * 3 + k] + wb * n[b * 3 + k] + wc * n[c * 3 + k];
      (e1[k] = p[b * 3 + k] - p[a * 3 + k]), (e2[k] = p[c * 3 + k] - p[a * 3 + k]);
    }
    const l = Math.hypot(nrm[0], nrm[1], nrm[2]) || 1;
    for (let k = 0; k < 3; k++) nrm[k] /= l;
    frame(e1, e2, d1, d2, nrm, T, B);
    return true;
  };
}
