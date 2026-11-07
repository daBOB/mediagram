// Fill the empty texels between texture islands with the colour of the nearest island texel.
// A simplified mesh's triangles reach slightly past their island, and smaller mip levels blend
// across the gaps; unpadded, both pick up Meshy's gap colour and show as white or dark specks.
// Run it on the full-detail mesh, before simplifying, so the islands' true extents are known.
import { createRequire } from 'node:module';
// sharp lives in the client's packages, not beside this file
const sharp = createRequire(new URL('../client/package.json', import.meta.url).pathname)('sharp');

/** Texels whose centre lies inside one of the primitives' UV triangles (glTF UV origin is top-left). */
function coverage(prims, w, h) {
  const mask = new Uint8Array(w * h);
  const a = [0, 0], b = [0, 0], c = [0, 0];
  for (const prim of prims) {
    const uv = prim.getAttribute('TEXCOORD_0');
    const idx = prim.getIndices().getArray();
    for (let t = 0; t < idx.length; t += 3) {
      uv.getElement(idx[t], a); uv.getElement(idx[t + 1], b); uv.getElement(idx[t + 2], c);
      const ax = a[0] * w, ay = a[1] * h, bx = b[0] * w, by = b[1] * h, cx = c[0] * w, cy = c[1] * h;
      const area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
      if (area === 0) continue;
      const x0 = Math.max(0, Math.floor(Math.min(ax, bx, cx))), x1 = Math.min(w - 1, Math.ceil(Math.max(ax, bx, cx)));
      const y0 = Math.max(0, Math.floor(Math.min(ay, by, cy))), y1 = Math.min(h - 1, Math.ceil(Math.max(ay, by, cy)));
      for (let y = y0; y <= y1; y++) {
        for (let x = x0; x <= x1; x++) {
          const px = x + 0.5, py = y + 0.5;
          const w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
          const w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
          if (w0 >= 0 && w1 >= 0 && w0 + w1 <= 1) mask[y * w + x] = 1;
        }
      }
    }
  }
  return mask;
}

/** Breadth-first fill outward from the covered texels (`mask` 1): every empty texel takes its nearest covered
 *  colour. Returns the share of texels that were covered. */
export function fillGaps(data, mask, w, h, ch) {
  const queue = new Int32Array(w * h);
  let head = 0, tail = 0;
  for (let p = 0; p < w * h; p++) if (mask[p]) queue[tail++] = p;
  const covered = tail;
  if (covered === 0) throw new Error('no texel is covered by the mesh UVs');
  while (head < tail) {
    const p = queue[head++], x = p % w;
    for (const q of [x > 0 ? p - 1 : -1, x < w - 1 ? p + 1 : -1, p - w, p + w]) {
      if (q < 0 || q >= w * h || mask[q]) continue;
      mask[q] = 1;
      for (let k = 0; k < ch; k++) data[q * ch + k] = data[p * ch + k];
      queue[tail++] = q;
    }
  }
  if (tail !== w * h) throw new Error('padding left texels unfilled');
  return covered / (w * h);
}

async function padImage(bytes, prims) {
  const { data, info } = await sharp(Buffer.from(bytes)).raw().toBuffer({ resolveWithObject: true });
  const { width: w, height: h, channels: ch } = info;
  const coveredShare = fillGaps(data, coverage(prims, w, h), w, h, ch);
  const png = await sharp(data, { raw: { width: w, height: h, channels: ch } }).png().toBuffer();
  return { png: new Uint8Array(png), coveredShare };
}

/** Pad the base colour and normal maps of every material, using the primitives drawn with it. */
export async function padTextureIslands(doc) {
  const prims = doc.getRoot().listMeshes().flatMap((m) => m.listPrimitives());
  for (const mat of doc.getRoot().listMaterials()) {
    const users = prims.filter((p) => p.getMaterial() === mat && p.getAttribute('TEXCOORD_0'));
    for (const [slot, tex] of [['baseColor', mat.getBaseColorTexture()], ['normal', mat.getNormalTexture()]]) {
      if (!tex || users.length === 0) continue;
      const { png, coveredShare } = await padImage(tex.getImage(), users);
      tex.setImage(png).setMimeType('image/png');
      console.log(`${mat.getName()} ${slot}: islands cover ${(coveredShare * 100).toFixed(1)}% of the texture, the rest padded`);
    }
  }
}
