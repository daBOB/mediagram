// Packs the baked geometry into one binary file (positions and uvs as floats, light as bytes, indices as 16 or 32 bits)
// and writes it with a JSON index beside the images. The TV reads both back with no work beyond decoding.
import { save, webp } from './util.mjs';

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
export async function saveImage(name, canvas, q = 0.86) { await save(`${name}.webp`, await webp(canvas, q)); }
