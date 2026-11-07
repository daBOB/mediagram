// Packs the baked geometry into one binary file (positions and uvs as floats, light as bytes, indices as 16 or 32 bits)
// and writes it with a JSON index beside the images. The TV reads both back with no work beyond decoding.
import { canvas as makeCanvas, png, save, webp } from './util.mjs';

/** The baked light can exceed 1 (low sun on a wall): bytes carry light / LIGHT_MAX, the TV multiplies it back. */
export const LIGHT_MAX = 1.25;

export class Pack {
  constructor() { this.chunks = []; this.len = 0; }
  // typed array → byte offset (4-aligned)
  add(arr) {
    const bytes = new Uint8Array(arr.buffer, arr.byteOffset, arr.byteLength), off = this.len;
    this.chunks.push(bytes); this.len += Math.ceil(bytes.length / 4) * 4;
    return off;
  }
  // geo: { pos, uv, col, tint?, idx }; returns the mesh record the TV reads
  mesh(geo) {
    const n = geo.pos.length / 3, u32 = n > 65535;
    const col = geo.col && Uint8Array.from(geo.col, (v) => Math.max(0, Math.min(255, Math.round((v / LIGHT_MAX) * 255))));
    return {
      n, pos: this.add(geo.pos), uv: this.add(geo.uv), col: geo.col ? this.add(col) : -1, tint: geo.tint ? this.add(geo.tint) : -1,
      idx: this.add(u32 ? geo.idx : Uint16Array.from(geo.idx)), count: geo.idx.length, u32,
    };
  }
  blob() {
    const out = new Uint8Array(this.len); let at = 0;
    for (const c of this.chunks) { out.set(c, at); at += Math.ceil(c.length / 4) * 4; }
    return new Blob([out]);
  }
}
/**
 * The triangles of a mesh drawn as one, nearest to `from` first: the GPU throws away what lies behind something it has already
 * drawn, so a hill or a wood drawn front to back is shaded once instead of two or three times.
 */
export function frontToBack(geo, from) {
  const tris = geo.idx.length / 3, key = new Float64Array(tris), p = geo.pos;
  for (let t = 0; t < tris; t++) {
    let x = 0, y = 0, z = 0;
    for (let k = 0; k < 3; k++) { const v = geo.idx[t * 3 + k] * 3; x += p[v]; y += p[v + 1]; z += p[v + 2]; }
    key[t] = Math.hypot(x / 3 - from[0], y / 3 - from[1], z / 3 - from[2]);
  }
  const order = Array.from({ length: tris }, (_, t) => t).sort((a, b) => key[a] - key[b]), idx = new Uint32Array(geo.idx.length);
  order.forEach((t, i) => idx.set(geo.idx.subarray(t * 3, t * 3 + 3), i * 3));
  return { ...geo, idx };
}
export async function saveImage(name, canvas, q = 0.86) { await save(`${name}.webp`, await webp(canvas, q)); }
// A texture the TV loads as KTX2. Compressed textures are not flipped on upload, but the town's uvs expect flipped images (the
// way three loads a picture), so the image is saved upside down. `rgb`: no alpha in it.
export async function saveKtx(name, canvas, rgb = false) {
  const flipped = makeCanvas(canvas.width, canvas.height), g = flipped.getContext('2d');
  g.translate(0, canvas.height); g.scale(1, -1); g.drawImage(canvas, 0, 0);
  await save(`${name}.png`, await png(flipped), rgb ? '?rgb' : '');
}
