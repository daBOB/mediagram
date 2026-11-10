// Darkens an avatar's colour map where its own shape hides it from the surrounding light (ambient occlusion):
// creases, the inside of ears and hoods, under the chin, between fingers and legs. The matcap studio light casts no
// shadows, so without it the avatars look flat. Computed per vertex on the full-detail mesh in its bind pose (rays
// against its own triangles), drawn into the colour map's texture space and multiplied in before the mesh is
// simplified and re-unwrapped, so the rebake carries it over like any other colour.
import { createRequire } from 'node:module';
import { fillGaps, forEachUvTexel } from './pad-texture-islands.mjs';

const sharp = createRequire(new URL('../client/package.json', import.meta.url).pathname)('sharp');

const RAYS = 64;
const RADIUS = 0.12; // of the mesh height: folds, creases and the shade a head casts on the chest, not whole limbs

/** A bounding-volume tree over the triangles: six floats of box per node; an inner node's two children sit at
 *  `first` and `first + 1`, a leaf holds `count` triangles from `first` in `order`. */
function buildBvh(p, idx) {
  const nt = idx.length / 3, order = new Uint32Array(nt), cen = new Float32Array(nt * 3);
  for (let t = 0; t < nt; t++) {
    order[t] = t;
    for (let k = 0; k < 3; k++) cen[t * 3 + k] = p[idx[t * 3] * 3 + k] + p[idx[t * 3 + 1] * 3 + k] + p[idx[t * 3 + 2] * 3 + k];
  }
  const box = new Float32Array(nt * 12), first = new Int32Array(nt * 2), count = new Int32Array(nt * 2);
  const stack = [[0, 0, nt]];
  let nodes = 1;
  while (stack.length) {
    const [n, s, e] = stack.pop(), o = n * 6;
    box.set([Infinity, Infinity, Infinity, -Infinity, -Infinity, -Infinity], o);
    for (let i = s; i < e; i++) {
      for (let c = 0; c < 3; c++) {
        const v = idx[order[i] * 3 + c] * 3;
        for (let k = 0; k < 3; k++) (box[o + k] = Math.min(box[o + k], p[v + k])), (box[o + 3 + k] = Math.max(box[o + 3 + k], p[v + k]));
      }
    }
    if (e - s <= 4) {
      (first[n] = s), (count[n] = e - s);
      continue;
    }
    const ext = [0, 1, 2].map((k) => box[o + 3 + k] - box[o + k]), ax = ext.indexOf(Math.max(...ext));
    order.set(Array.from(order.subarray(s, e)).sort((a, b) => cen[a * 3 + ax] - cen[b * 3 + ax]), s);
    const m = (s + e) >> 1;
    first[n] = nodes;
    stack.push([nodes, s, m], [nodes + 1, m, e]);
    nodes += 2;
  }
  return { box, first, count, order, idx, p };
}

const STACK = new Int32Array(128);
/** Whether a ray from o along unit d hits any triangle between tMin and tMax. */
function blocked({ box, first, count, order, idx, p }, o, d, tMin, tMax) {
  const ix = 1 / d[0], iy = 1 / d[1], iz = 1 / d[2];
  let sp = 0;
  STACK[sp++] = 0;
  while (sp) {
    const n = STACK[--sp], b = n * 6;
    let t0 = (box[b] - o[0]) * ix, t1 = (box[b + 3] - o[0]) * ix;
    let lo = Math.min(t0, t1), hi = Math.max(t0, t1);
    (t0 = (box[b + 1] - o[1]) * iy), (t1 = (box[b + 4] - o[1]) * iy);
    (lo = Math.max(lo, Math.min(t0, t1))), (hi = Math.min(hi, Math.max(t0, t1)));
    (t0 = (box[b + 2] - o[2]) * iz), (t1 = (box[b + 5] - o[2]) * iz);
    (lo = Math.max(lo, Math.min(t0, t1))), (hi = Math.min(hi, Math.max(t0, t1)));
    if (Math.max(lo, 0) > Math.min(hi, tMax)) continue;
    if (count[n] === 0) {
      (STACK[sp++] = first[n]), (STACK[sp++] = first[n] + 1);
      continue;
    }
    for (let i = first[n]; i < first[n] + count[n]; i++) {
      // Möller–Trumbore
      const a = idx[order[i] * 3] * 3, bv = idx[order[i] * 3 + 1] * 3, c = idx[order[i] * 3 + 2] * 3;
      const e1x = p[bv] - p[a], e1y = p[bv + 1] - p[a + 1], e1z = p[bv + 2] - p[a + 2];
      const e2x = p[c] - p[a], e2y = p[c + 1] - p[a + 1], e2z = p[c + 2] - p[a + 2];
      const px = d[1] * e2z - d[2] * e2y, py = d[2] * e2x - d[0] * e2z, pz = d[0] * e2y - d[1] * e2x;
      const det = e1x * px + e1y * py + e1z * pz;
      if (Math.abs(det) < 1e-12) continue;
      const inv = 1 / det, sx = o[0] - p[a], sy = o[1] - p[a + 1], sz = o[2] - p[a + 2];
      const u = (sx * px + sy * py + sz * pz) * inv;
      if (u < 0 || u > 1) continue;
      const qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
      const v = (d[0] * qx + d[1] * qy + d[2] * qz) * inv;
      if (v < 0 || u + v > 1) continue;
      const t = (e2x * qx + e2y * qy + e2z * qz) * inv;
      if (t > tMin && t < tMax) return true;
    }
  }
  return false;
}

/** The share of a cosine-weighted hemisphere around each vertex's normal that is open within `radius` (1 = open).
 *  Vertices at one position (the copies along texture seams) are computed once. */
function vertexOcclusion(p, nrm, idx, height, radius) {
  const bvh = buildBvh(p, idx), n = p.length / 3, out = new Float32Array(n), seen = new Map();
  const o = [0, 0, 0], d = [0, 0, 0], tg = [0, 0, 0], bt = [0, 0, 0];
  for (let v = 0; v < n; v++) {
    const key = `${p[v * 3]},${p[v * 3 + 1]},${p[v * 3 + 2]}`;
    if (seen.has(key)) {
      out[v] = out[seen.get(key)];
      continue;
    }
    seen.set(key, v);
    const nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
    // any tangent frame around the normal
    const s = Math.abs(nx) < 0.9 ? [1, 0, 0] : [0, 1, 0];
    (tg[0] = ny * s[2] - nz * s[1]), (tg[1] = nz * s[0] - nx * s[2]), (tg[2] = nx * s[1] - ny * s[0]);
    const l = Math.hypot(...tg);
    for (let k = 0; k < 3; k++) tg[k] /= l;
    (bt[0] = ny * tg[2] - nz * tg[1]), (bt[1] = nz * tg[0] - nx * tg[2]), (bt[2] = nx * tg[1] - ny * tg[0]);
    for (let k = 0; k < 3; k++) o[k] = p[v * 3 + k] + nrm[v * 3 + k] * height * 1e-4;
    const turn = (v * 0.618034) % 1; // spins the pattern per vertex so neighbours sample different directions
    let open = 0;
    for (let i = 0; i < RAYS; i++) {
      const r = Math.sqrt((i + 0.5) / RAYS), phi = 2 * Math.PI * ((i * 0.618034 + turn) % 1), up = Math.sqrt(1 - r * r);
      const x = r * Math.cos(phi), y = r * Math.sin(phi);
      for (let k = 0; k < 3; k++) d[k] = tg[k] * x + bt[k] * y + nrm[v * 3 + k] * up;
      if (!blocked(bvh, o, d, height * 1e-5, height * radius)) open++;
    }
    out[v] = open / RAYS;
  }
  return out;
}

/** Multiplies ambient occlusion into the colour map of the one textured primitive, `strength` 0..1 of it.
 *  `radius` is the reach of the occluders as a share of the mesh height. Run on the full-detail mesh with its own
 *  texture coordinates, before simplifying. */
export async function bakeAmbientOcclusion(doc, strength, radius = RADIUS) {
  const prim = doc.getRoot().listMeshes().flatMap((m) => m.listPrimitives()).find((x) => x.getMaterial()?.getBaseColorTexture());
  if (!prim) throw new Error('no textured primitive');
  const posAcc = prim.getAttribute('POSITION'), nrm = prim.getAttribute('NORMAL')?.getArray();
  const p = posAcc.getArray(), idx = prim.getIndices().getArray();
  if (!(p instanceof Float32Array) || !(nrm instanceof Float32Array)) throw new Error('bake before quantizing, with normals');
  const height = posAcc.getMax([])[1] - posAcc.getMin([])[1];
  const open = vertexOcclusion(p, nrm, idx, height, radius);
  const tex = prim.getMaterial().getBaseColorTexture();
  const { data, info } = await sharp(Buffer.from(tex.getImage())).raw().toBuffer({ resolveWithObject: true });
  const { width: w, height: h, channels: ch } = info;
  const ao = new Uint8Array(w * h), mask = new Uint8Array(w * h);
  forEachUvTexel(prim.getAttribute('TEXCOORD_0').getArray(), idx, w, h, (i, t, w0, w1, w2) => {
    ao[i] = Math.round(255 * (w0 * open[idx[t]] + w1 * open[idx[t + 1]] + w2 * open[idx[t + 2]]));
    mask[i] = 1;
  });
  fillGaps(ao, mask, w, h, 1); // the gaps between islands take their neighbour's shade, so seams stay clean
  // darken in linear light: the map holds sRGB values
  const gain = Float32Array.from({ length: 256 }, (_, a) => Math.pow(1 - strength * (1 - a / 255), 1 / 2.2));
  for (let i = 0; i < w * h; i++) for (let k = 0; k < Math.min(ch, 3); k++) data[i * ch + k] = Math.round(data[i * ch + k] * gain[ao[i]]);
  tex.setImage(new Uint8Array(await sharp(data, { raw: { width: w, height: h, channels: ch } }).png().toBuffer())).setMimeType('image/png');
  let sum = 0;
  for (const a of open) sum += a;
  return `ambient occlusion: mean openness ${(sum / open.length).toFixed(2)}, strength ${strength}`;
}
