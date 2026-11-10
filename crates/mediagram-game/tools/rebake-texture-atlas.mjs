// Re-unwraps an avatar into a few large, padded texture charts and bakes its colour and normal maps onto them.
// Meshy packs thousands of small islands edge to edge: every smaller mip level blends neighbours (white scratches
// along the seams on the TV). Every triangle must lie inside one old island (Meshy's own mesh, or one simplified with
// its seams locked), so its old texture coordinates interpolate correctly across it.
import { createRequire } from 'node:module';
import { fillGaps } from './pad-texture-islands.mjs';

const req = createRequire(new URL('../client/package.json', import.meta.url).pathname);
const sharp = req('sharp');

let atlasApi = null;
async function xatlas() {
  if (!atlasApi) {
    const { Api } = await import(req.resolve('xatlasjs/dist/node/api.mjs'));
    const Xatlas = Api(req('xatlasjs/dist/node/xatlas.js'));
    atlasApi = await new Promise((resolve) => {
      const a = new Xatlas(() => resolve(a));
    });
  }
  return atlasApi;
}

const raw = (image) => sharp(Buffer.from(image)).ensureAlpha().raw().toBuffer({ resolveWithObject: true });

/** Bilinear sample of an RGBA image at texture coordinates (u, v), edges clamped. */
function sample({ data: img, info: { width: w, height: h } }, u, v, out, o) {
  const x = Math.min(w - 1, Math.max(0, u * w - 0.5)), y = Math.min(h - 1, Math.max(0, v * h - 0.5));
  const x0 = Math.floor(x), y0 = Math.floor(y), x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
  const fx = x - x0, fy = y - y0;
  for (let k = 0; k < 4; k++) {
    const top = img[(y0 * w + x0) * 4 + k] * (1 - fx) + img[(y0 * w + x1) * 4 + k] * fx;
    const bottom = img[(y1 * w + x0) * 4 + k] * (1 - fx) + img[(y1 * w + x1) * 4 + k] * fx;
    out[o + k] = Math.round(top * (1 - fy) + bottom * fy);
  }
}

const cross = (a, b, o) => ((o[0] = a[1] * b[2] - a[2] * b[1]), (o[1] = a[2] * b[0] - a[0] * b[2]), (o[2] = a[0] * b[1] - a[1] * b[0]), o);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const q0 = [0, 0, 0], q1 = [0, 0, 0], tmp = [0, 0, 0];

/** The tangent frame three.js derives on screen for a mesh without tangents (perturbNormal2Arb): the gradients of u
 *  and v across the triangle (edges e1, e2, texture steps d1, d2) around the shading normal, scaled together. */
export function frame(e1, e2, d1, d2, nrm, T, B) {
  cross(e2, nrm, q1);
  cross(nrm, e1, q0);
  for (let k = 0; k < 3; k++) (T[k] = q1[k] * d1[0] + q0[k] * d2[0]), (B[k] = q1[k] * d1[1] + q0[k] * d2[1]);
  const s = 1 / Math.sqrt(Math.max(dot(T, T), dot(B, B)) || 1);
  for (let k = 0; k < 3; k++) (T[k] *= s), (B[k] *= s);
}

/** One normal-map texel moved from the old frame (To, Bo, normal nf) to the new chart's (Tn, Bn, normal nt), so the
 *  surface lights as before: three reads the old map through normalScale (scale, -scale), the new through (1, -1). */
function moveNormal(m, scale, To, Bo, nf, Tn, Bn, nt, out, o) {
  const x = (m[0] / 127.5 - 1) * scale, y = -(m[1] / 127.5 - 1) * scale, z = m[2] / 127.5 - 1;
  const w = [To[0] * x + Bo[0] * y + nf[0] * z, To[1] * x + Bo[1] * y + nf[1] * z, To[2] * x + Bo[2] * y + nf[2] * z];
  const det = dot(Tn, cross(Bn, nt, tmp));
  let a = 0, b = 0, c = 1;
  if (Math.abs(det) > 1e-12) (a = dot(w, tmp) / det), (b = dot(w, cross(nt, Tn, tmp)) / det), (c = dot(w, cross(Tn, Bn, tmp)) / det);
  const len = Math.hypot(a, b, c) || 1;
  out[o] = Math.round((a / len + 1) * 127.5);
  out[o + 1] = Math.round((-b / len + 1) * 127.5);
  out[o + 2] = Math.round((Math.max(0, c) / len + 1) * 127.5);
  out[o + 3] = 255;
}

/** One primitive: new charts from xatlas, then each new triangle filled from the old maps at its old coordinates. */
async function rebakePrimitive(doc, prim, colour, normal, size, padding, fullFrame) {
  if (prim.listTargets().length) throw new Error('morph targets are not carried over');
  const pos = prim.getAttribute('POSITION'), oldUv = prim.getAttribute('TEXCOORD_0'), nor = prim.getAttribute('NORMAL');
  if (normal && !nor) throw new Error('a normal map needs vertex normals');
  const idx = prim.getIndices().getArray(), n = pos.getCount();
  // weld the island borders' duplicates (bit-identical positions) so the charts follow the surface, not Meshy's islands
  const byPos = new Map(), welded = new Uint32Array(n), wPos = [], p = [0, 0, 0];
  for (let v = 0; v < n; v++) {
    pos.getElement(v, p);
    const k = p.join();
    if (!byPos.has(k)) byPos.set(k, wPos.length / 3), wPos.push(...p);
    welded[v] = byPos.get(k);
  }
  if (wPos.length / 3 > 65535) throw new Error('xatlas.js takes 16-bit indices: simplify further first');
  const xa = await xatlas();
  xa.createAtlas();
  xa.addMesh(Uint16Array.from(idx, (v) => welded[v]), new Float32Array(wPos));
  // brute-force packing with block alignment: 60% of the map in charts instead of 50%
  const atlas = xa.generateAtlas({}, { resolution: size, padding, bilinear: true, bruteForce: true, blockAlign: true });
  xa.destroyAtlas();
  const { index, vertex, oldIndexes, vertexCount } = atlas.meshes[0];
  if (atlas.atlasCount !== 1 || index.length !== idx.length) throw new Error(`xatlas made ${atlas.atlasCount} atlases, ${index.length} indices`);
  if (vertexCount > 65535) throw new Error('xatlas.js returns 16-bit indices: simplify further first');
  if (!(oldUv.getArray() instanceof Float32Array)) throw new Error('rebake before quantizing');
  // each new vertex takes its other attributes from an old vertex at one of its corners
  const from = new Int32Array(vertexCount).fill(-1);
  for (let c = 0; c < idx.length; c++) {
    if (oldIndexes[index[c]] !== welded[idx[c]]) throw new Error('xatlas reordered the triangle corners');
    if (from[index[c]] < 0) from[index[c]] = idx[c];
  }

  const srcC = await raw(colour), srcN = normal && (await raw(normal.image));
  const outC = new Uint8Array(size * size * 4), outN = normal && new Uint8Array(size * size * 4), mask = new Uint8Array(size * size);
  // the wasm build hands back coordinates divided by the atlas size, which only roughly matches the one asked for
  // (2451 for 2048): they are drawn onto `size` regardless, and the padding shrinks by the same factor
  const uv = vertex.coords1;
  if (uv.some((x) => x < 0 || x > 1)) throw new Error('xatlas coordinates outside 0..1');
  const ou = [[0, 0], [0, 0], [0, 0]], ps = [[0, 0, 0], [0, 0, 0], [0, 0, 0]], ns = [[0, 0, 0], [0, 0, 0], [0, 0, 0]];
  const e1 = [0, 0, 0], e2 = [0, 0, 0], nrm = [0, 0, 0], To = [0, 0, 0], Bo = [0, 0, 0], Tn = [0, 0, 0], Bn = [0, 0, 0], nf = [0, 0, 0], pt = [0, 0, 0];
  const m = new Uint8Array(4), od1 = [0, 0], od2 = [0, 0], nd1 = [0, 0], nd2 = [0, 0];
  for (let t = 0; t < idx.length; t += 3) {
    for (let k = 0; k < 3; k++) oldUv.getElement(idx[t + k], ou[k]), pos.getElement(idx[t + k], ps[k]), nor?.getElement(idx[t + k], ns[k]);
    const [ax, ay, bx, by, cx, cy] = [0, 1, 2].flatMap((k) => [uv[index[t + k] * 2] * size, uv[index[t + k] * 2 + 1] * size]);
    const area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    if (area === 0) continue;
    for (let k = 0; k < 3; k++) (e1[k] = ps[1][k] - ps[0][k]), (e2[k] = ps[2][k] - ps[0][k]);
    for (let k = 0; k < 2; k++) (od1[k] = ou[1][k] - ou[0][k]), (od2[k] = ou[2][k] - ou[0][k]);
    (nd1[0] = bx - ax), (nd1[1] = by - ay), (nd2[0] = cx - ax), (nd2[1] = cy - ay);
    const x0 = Math.max(0, Math.floor(Math.min(ax, bx, cx))), x1 = Math.min(size - 1, Math.ceil(Math.max(ax, bx, cx)));
    const y0 = Math.max(0, Math.floor(Math.min(ay, by, cy))), y1 = Math.min(size - 1, Math.ceil(Math.max(ay, by, cy)));
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        const px = x + 0.5, py = y + 0.5;
        const w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
        const w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
        const w2 = 1 - w0 - w1;
        if (w0 < 0 || w1 < 0 || w2 < 0) continue;
        const u = w0 * ou[0][0] + w1 * ou[1][0] + w2 * ou[2][0], v = w0 * ou[0][1] + w1 * ou[1][1] + w2 * ou[2][1];
        const o = (y * size + x) * 4;
        sample(srcC, u, v, outC, o);
        if (normal) {
          for (let k = 0; k < 3; k++) (nrm[k] = w0 * ns[0][k] + w1 * ns[1][k] + w2 * ns[2][k]), (pt[k] = w0 * ps[0][k] + w1 * ps[1][k] + w2 * ps[2][k]);
          const l = Math.hypot(...nrm) || 1;
          for (let k = 0; k < 3; k++) nrm[k] /= l;
          frame(e1, e2, nd1, nd2, nrm, Tn, Bn);
          // the old map belongs to the full mesh's surface: read it in that frame where known
          if (!fullFrame?.(u, v, pt, nf, To, Bo)) frame(e1, e2, od1, od2, nrm, To, Bo), nf.splice(0, 3, ...nrm);
          sample(srcN, u, v, m, 0);
          moveNormal(m, normal.scale, To, Bo, nf, Tn, Bn, nrm, outN, o);
        }
        mask[y * size + x] = 1;
      }
    }
  }
  if (normal) fillGaps(outN, mask.slice(), size, size, 4);
  const covered = fillGaps(outC, mask, size, size, 4);

  for (const semantic of prim.listSemantics()) {
    const acc = prim.getAttribute(semantic), es = acc.getElementSize(), old = acc.getArray();
    const arr = new old.constructor(vertexCount * es);
    for (let v = 0; v < vertexCount; v++) arr.set(old.subarray(from[v] * es, from[v] * es + es), v * es);
    if (semantic === 'TEXCOORD_0') arr.set(uv);
    prim.setAttribute(semantic, doc.createAccessor().setType(acc.getType()).setArray(arr).setNormalized(acc.getNormalized()));
  }
  prim.setIndices(doc.createAccessor().setType('SCALAR').setArray(index));
  const png = async (img) => new Uint8Array(await sharp(img, { raw: { width: size, height: size, channels: 4 } }).png().toBuffer());
  return { colour: await png(outC), normal: normal && (await png(outN)), before: n, after: vertexCount, covered };
}

/** Every textured material's one primitive gets new charts and `size`-square colour and normal maps with `padding`
 *  texels between charts (asked of xatlas, whose atlas comes out ~15% larger and is drawn at `size`: ~5 texels, enough
 *  for the mip levels the TV samples an avatar at). `fullFrame` (full-detail-normal-frames.mjs) bakes the normal map
 *  against the mesh before simplifying. */
export async function rebakeTextureAtlas(doc, size, padding = 6, fullFrame = null) {
  const root = doc.getRoot();
  const prims = root.listMeshes().flatMap((m) => m.listPrimitives());
  const done = [];
  for (const mat of root.listMaterials()) {
    const tex = mat.getBaseColorTexture(), nTex = mat.getNormalTexture();
    const users = prims.filter((p) => p.getMaterial() === mat);
    if (!tex || users.length === 0) continue;
    if (users.length > 1) throw new Error(`${mat.getName()}: ${users.length} primitives share one map (one expected)`);
    if (mat.getMetallicRoughnessTexture() || mat.getOcclusionTexture() || mat.getEmissiveTexture()) throw new Error('only colour and normal maps are rebaked');
    const normal = nTex && { image: nTex.getImage(), scale: mat.getNormalScale() };
    const r = await rebakePrimitive(doc, users[0], tex.getImage(), normal, size, padding, fullFrame);
    tex.setImage(r.colour).setMimeType('image/png');
    if (nTex) nTex.setImage(r.normal).setMimeType('image/png'), mat.setNormalScale(1); // the scale is baked in
    done.push(`${r.before} -> ${r.after} vertices, charts cover ${(r.covered * 100).toFixed(0)}%`);
  }
  if (done.length === 0) throw new Error('no textured material');
  return done.join('; ');
}
