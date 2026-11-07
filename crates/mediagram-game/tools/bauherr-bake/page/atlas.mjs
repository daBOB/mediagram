// Every building part is one geometry with one material: each visible face gets its own painted rect in a shared
// canvas atlas (windows, plaster, roof tiles), small trim maps to flat-colour swatches, and the light is baked into
// vertex colours. Lit windows and signs are marked in the atlas alpha (alpha < 1 = self-lit), which the TV's town
// material reads instead of the vertex light. Parts are built in lot-local units and placed in the world on finish.
import * as THREE from 'three';
import { litLog } from './paint.mjs';
import { canvas } from './util.mjs';

const PAD = 4;   // bleed round every rect, so mipmaps of the minified facades do not mix neighbours

export class Atlas {
  constructor(w, h, ppu, bg = '#6d6a64') { Object.assign(this, { w, h, ppu, bg, jobs: [], sw: new Map() }); }
  // a face wu × hu units, painted later by paint(g, wu, hu) in face units (x right, y down from its top edge)
  face(wu, hu, paint, ppu = this.ppu) {
    const r = { pw: Math.max(2, Math.round(wu * ppu)), ph: Math.max(2, Math.round(hu * ppu)), wu, hu, paint };
    this.jobs.push(r); return r;
  }
  // a flat colour many small faces share (cornices, posts, rails, frames)
  swatch(css) {
    if (!this.sw.has(css)) this.sw.set(css, this.face(1, 1, (g) => { g.fillStyle = css; g.fillRect(-1, -1, 3, 3); }, 8));
    return this.sw.get(css);
  }
  // shelf packing, tallest first; if it does not fit, every face shrinks a little and it packs again
  pack() {
    const size = this.jobs.map((r) => [r.pw, r.ph]);
    for (this.scale = 1; ; this.scale *= 0.94) {
      this.jobs.forEach((r, i) => { r.pw = Math.max(2, Math.round(size[i][0] * this.scale)); r.ph = Math.max(2, Math.round(size[i][1] * this.scale)); });
      let x = 0, y = 0, row = 0, ok = true;
      for (const r of [...this.jobs].sort((a, b) => b.ph - a.ph)) {
        const W = r.pw + 2 * PAD, H = r.ph + 2 * PAD;
        if (x + W > this.w) { x = 0; y += row; row = 0; }
        if (y + H > this.h) { ok = false; break; }
        r.x = x + PAD; r.y = y + PAD; x += W; row = Math.max(row, H);
      }
      if (ok) { this.used = (y + row) / this.h; return; }
    }
  }
  draw() {
    const c = canvas(this.w, this.h), g = c.getContext('2d');
    for (const r of this.jobs) {
      g.save(); g.beginPath(); g.rect(r.x - PAD, r.y - PAD, r.pw + 2 * PAD, r.ph + 2 * PAD); g.clip();
      g.fillStyle = this.bg; g.fillRect(r.x - PAD, r.y - PAD, r.pw + 2 * PAD, r.ph + 2 * PAD);   // opaque under any painter that leaves the bleed empty
      g.translate(r.x, r.y); g.scale(r.pw / r.wu, r.ph / r.hu); litLog.rect = r; r.lit = []; r.paint(g, r.wu, r.hu, r); g.restore();
    }
    litLog.rect = null;
    return c;
  }
  uv(r, s, t) { return [(r.x + s * r.pw) / this.w, 1 - (r.y + t * r.ph) / this.h]; }
}

const V = THREE.Vector3, Y = new V(0, 1, 0);
export class Part {
  // o: { rot, x, z, scale } place the part in the world on finish (scale: world units per local unit); tint: 1 = the owner's colour tints what is built here
  constructor(atlas, light, o = {}) { Object.assign(this, { atlas, light, rot: o.rot ?? 0, x: o.x ?? 0, z: o.z ?? 0, S: o.scale ?? 1, tint: o.tint ?? 0, verts: [], idx: [] }); }
  // how many indices are built so far: stage ranges are read off this
  get mark() { return this.idx.length; }
  // a convex planar polygon, points counter-clockwise seen from outside; fill = a swatch css or paint(g, wu, hu)
  poly(pts, fill, o = {}) {
    const p = pts.map((q) => (q.isVector3 ? q : new V(...q))), n = new V();
    for (let i = 0; i < p.length; i++) { const a = p[i], b = p[(i + 1) % p.length]; n.x += (a.y - b.y) * (a.z + b.z); n.y += (a.z - b.z) * (a.x + b.x); n.z += (a.x - b.x) * (a.y + b.y); }
    n.normalize();
    const right = Math.abs(n.y) > 0.99 ? new V(1, 0, 0) : new V().crossVectors(Y, n).normalize(), up = new V().crossVectors(n, right);
    const s = p.map((q) => q.dot(right)), t = p.map((q) => q.dot(up));
    const s0 = Math.min(...s), s1 = Math.max(...s), t0 = Math.min(...t), t1 = Math.max(...t), sw = typeof fill === 'string';
    const r = sw ? this.atlas.swatch(fill) : this.atlas.face(s1 - s0, t1 - t0, fill, o.ppu);
    if (!sw) r.face = { P: this, n, right, up, s0, t1, d: p[0].dot(n) };   // roofs read their orientation from it
    const base = this.verts.length;
    p.forEach((q, i) => this.verts.push({ q, n: o.normals?.[i] ?? n, r, s: sw ? 0.5 : (s[i] - s0) / (s1 - s0), t: sw ? 0.5 : (t1 - t[i]) / (t1 - t0), ao: o.ao ?? 1, sun: o.sun ?? 1, tint: o.tint ?? this.tint }));
    for (let i = 1; i < p.length - 1; i++) this.idx.push(base, base + i, base + i + 1);
    return r;
  }
  // a wall-like quad from (x0,z0) to (x1,z1) seen from outside left→right, from y0 up to y1
  wall(x0, z0, x1, z1, y0, y1, fill, o) { return this.poly([[x0, y0, z0], [x1, y0, z1], [x1, y1, z1], [x0, y1, z0]], fill, o); }
  // an axis box; faces: which of 'px nx py ny pz nz' to make (bottom skipped by default)
  box(x0, y0, z0, x1, y1, z1, fill, o = {}) {
    const f = o.faces ?? 'px nx py pz nz';
    if (f.includes('pz')) this.wall(x0, z1, x1, z1, y0, y1, o.front ?? fill, o);
    if (f.includes('nz')) this.wall(x1, z0, x0, z0, y0, y1, fill, o);
    if (f.includes('px')) this.wall(x1, z1, x1, z0, y0, y1, o.side ?? fill, o);
    if (f.includes('nx')) this.wall(x0, z0, x0, z1, y0, y1, o.side ?? fill, o);
    if (f.includes('py')) this.poly([[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]], o.top ?? fill, o);
    if (f.includes('ny')) this.poly([[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]], fill, o);
  }
  // an upright cylinder or cone frustum, smooth-lit; fill a swatch, or paint for one rect wrapped round it
  cyl(cx, cz, r0, r1, y0, y1, seg, fill, o = {}) {
    const sw = typeof fill === 'string', r = sw ? this.atlas.swatch(fill) : this.atlas.face(2 * Math.PI * r0, y1 - y0, fill), base = this.verts.length;
    for (let i = 0; i <= seg; i++) {
      const a = (i / seg) * Math.PI * 2, cx_ = Math.sin(a), cz_ = Math.cos(a), n = new V(cx_, (r0 - r1) / (y1 - y0), cz_).normalize();
      this.verts.push({ q: new V(cx + cx_ * r0, y0, cz + cz_ * r0), n, r, s: sw ? 0.5 : i / seg, t: sw ? 0.5 : 1, ao: o.ao ?? 1, sun: 1, tint: o.tint ?? this.tint });
      this.verts.push({ q: new V(cx + cx_ * r1, y1, cz + cz_ * r1), n, r, s: sw ? 0.5 : i / seg, t: sw ? 0.5 : 0, ao: o.ao ?? 1, sun: 1, tint: o.tint ?? this.tint });
      if (i < seg) { const k = base + i * 2; this.idx.push(k, k + 2, k + 3, k, k + 3, k + 1); }
    }
    if (o.cap) this.disc(cx, cz, r1, y1, seg, o.cap);
  }
  disc(cx, cz, r, y, seg, fill) { this.poly(Array.from({ length: seg }, (_, i) => { const a = (i / seg) * Math.PI * 2; return [cx + Math.sin(a) * r, y, cz + Math.cos(a) * r]; }), fill); }
  // raw geometry (already in part space, with normals) lit and mapped to a swatch: figures, odd shapes
  mesh(geo, css, o = {}) {
    const r = this.atlas.swatch(css), g = geo.index ? geo.toNonIndexed() : geo, P = g.attributes.position, N = g.attributes.normal, base = this.verts.length;
    for (let i = 0; i < P.count; i++) this.verts.push({ q: new V().fromBufferAttribute(P, i), n: new V().fromBufferAttribute(N, i), r, s: 0.5, t: 0.5, ao: o.ao ?? 1, sun: 1, tint: o.tint ?? this.tint });
    for (let i = 0; i < P.count; i++) this.idx.push(base + i);
  }
  // after atlas.pack(): world positions, atlas uvs, baked light (the world normal is the part normal turned by the lot)
  finish() {
    const n = this.verts.length, pos = new Float32Array(n * 3), uv = new Float32Array(n * 2), col = new Float32Array(n * 3), tint = new Uint8Array(n);
    const c = Math.cos(this.rot), s = Math.sin(this.rot);
    this.verts.forEach((v, i) => {
      pos.set([this.x + (v.q.x * c + v.q.z * s) * this.S, v.q.y * this.S, this.z + (v.q.z * c - v.q.x * s) * this.S], i * 3);
      uv.set(this.atlas.uv(v.r, v.s, v.t), i * 2);
      const nx = v.n.x * c + v.n.z * s, nz = -v.n.x * s + v.n.z * c;
      col.set(this.light.at(nx, v.n.y, nz, v.sun, v.ao * this.light.groundAO(v.q.y * this.S)), i * 3);
      tint[i] = v.tint ? 1 : 0;
    });
    return { pos, uv, col, tint, idx: Uint32Array.from(this.idx) };
  }
  // part-local point → world [x, y, z]
  world([x, y, z]) { const c = Math.cos(this.rot), s = Math.sin(this.rot); return [this.x + (x * c + z * s) * this.S, y * this.S, this.z + (z * c - x * s) * this.S]; }
}
