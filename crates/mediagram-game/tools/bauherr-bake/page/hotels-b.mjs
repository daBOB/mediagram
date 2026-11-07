// Four more hotels: the Stadtpalais city palace, the Sternwarte observatory hotel, the Kurhaus spa house and the
// Leuchtturm lighthouse hotel. Our own designs; each stage adds a wing or tower, the last lays out the leisure facility.
import * as THREE from 'three';
import { block, slab, pool, deck, walls, SLOT as B, corners } from './hotel-kit.mjs';
import { flat, windowAt, glow, facade } from './paint.mjs';
import * as K from './props.mjs';
import * as OP from './owner-props.mjs';

const scaf = (b, top) => ({ box: b, h: top - 0.1 });
const leisure = (extra) => ({ axis: 'x', x0: B.L.x0 - 0.15, x1: B.L.x1 + 0.15, ...extra });
const sign = (c, b, x, y, w, h) => OP.roofSign(c.O, x, b.z1 + 0.02, y, w, c.name, c.signLit, h);
const COPPER = '#6f9c8a';
// a dome on a drum: weathered copper over a cream drum, with a dark slit
function dome(P, cx, cz, r, y, drum = 0.1, css = COPPER) {
  P.cyl(cx, cz, r, r, y, y + drum, 16, '#e9e1cf');
  const g = new THREE.SphereGeometry(r, 18, 8, 0, Math.PI * 2, 0, Math.PI / 2).translate(cx, y + drum, cz);
  P.mesh(g, css, { ao: 1 });
  P.box(cx - 0.012, y + drum + r * 0.2, cz + r * 0.5, cx + 0.012, y + drum + r * 0.92, cz + r * 0.86, '#23282e', { faces: 'pz' });
}
const pyramid = (P, b, y, rise, css) => { const mx = (b.x0 + b.x1) / 2, mz = (b.z0 + b.z1) / 2; for (const [a, c] of [[[b.x0, b.z1], [b.x1, b.z1]], [[b.x1, b.z1], [b.x1, b.z0]], [[b.x1, b.z0], [b.x0, b.z0]], [[b.x0, b.z0], [b.x0, b.z1]]]) P.poly([[a[0], y, a[1]], [c[0], y, c[1]], [mx, y + rise, mz]], css); };

// ---------- Stadtpalais: Gründerzeit palace in ochre over rusticated sandstone, mansard, corner pavilions, parterre and roof garden ----------
export const stadtpalais = (name) => ({
  name, atlas: [2048, 1536],
  stages: [
    (c) => {
      const S = { mat: 'plaster', tint: '#dcc08c', tile: 1.2, groundH: 0.32, floorH: 0.27, top: 0.1, pitch: 0.21, lit: c.lit, glowK: c.glowK, bands: true, nobile: 'tri',
        win: { w: 0.085, h: 0.16, frame: '#efe8da', mull: 'cross', ped: 'flat', pedColor: '#e8dcc0' }, groundWin: { w: 0.1, h: 0.17, frame: '#efe8da', mull: 'cross', arch: true },
        plinth: { mat: 'sandstone', h: 0.32, tile: 0.45, tint: '#e9dcc4' }, bandColor: 'rgba(245,235,212,0.8)', doorArch: true, doorFrame: '#5b3a24' };
      const roof = { mat: 'slate', tile: 0.45, tint: '#7d8490' };
      c.st = { S, ring: true, trim: '#e8dcc0', roof: { kind: 'mansard', spec: roof, upper: { ...roof, tint: '#6f7680' }, lower: 0.28, rise: 0.08 }, dormers: [-0.5, -0.25, 0.25, 0.5] };
      const b = B.main, r = block(c, b, 1.18, c.st, { door: true, awn: [-0.56, -0.38, 0.38, 0.56], awnY: 0.24, awnW: 0.11 });
      c.P.box(-0.22, 0, b.z1, 0.22, 1.2, b.z1 + 0.05, '#d3b884', { faces: 'pz px nx', front: facade({ ...c.st.S, pitch: 0.15 }, { door: true }) });
      K.balcony(c.P, -0.24, 0.24, b.z1 + 0.05, 0.33, 0.06, K.railing('#e9e1cf', '#b8ab90'), '#e8dcc0');
      sign(c, b, 0, 1.4, 0.9, 0.24); OP.flag(c.O, -0.3, b.z1 + 0.06, 0.36, 0.32); OP.flag(c.O, 0.3, b.z1 + 0.06, 0.36, 0.32);
      return { ...r, scaf: scaf(b, r.top) };
    },
    ...['A', 'B'].map((k) => (c) => {   // wings with a flat roof behind a balustrade: the roof garden goes here later
      const b = B[k], r = block(c, b, 0.86, { ...c.st, roof: { kind: 'flat', spec: { mat: 'slate', tile: 0.5, tint: '#6f7680' } }, dormers: [] }, { awn: [(b.x0 + b.x1) / 2], awnW: 0.14, awnY: 0.24 });
      return { ...r, scaf: scaf(b, r.top) };
    }),
    ...['C', 'D'].map((k) => (c) => {   // corner pavilions with pyramid roofs
      const b = { ...B[k], z0: B[k].z0 - 0.0 }, h = 1.3, r = block(c, b, h, { ...c.st, roof: { kind: 'flat' } }, { balc: false });
      pyramid(c.P, { x0: b.x0 - 0.04, x1: b.x1 + 0.04, z0: b.z0 - 0.04, z1: b.z1 + 0.04 }, h, 0.34, '#6f7680');
      return { ...r, top: h + 0.4, cast: [...r.cast, [(b.x0 + b.x1) / 2, h + 0.34, (b.z0 + b.z1) / 2]], scaf: scaf(b, h + 0.3) };
    }),
    (c) => {   // a box-hedge parterre in front, a basin, clipped yews; planters on the wing roofs
      const b = B.L, y = 0.008;
      slab(c.P, { x0: -1.5, x1: 1.5, z0: 0.5, z1: 1.45 }, y, flat({ mat: 'gravel', tile: 0.5, tint: '#e6dcc6' }));
      for (const sx of [-1, 1]) for (const sz of [0.55, 1.1]) K.hedge(c.P, sx * 0.14 + (sx < 0 ? -0.55 : 0), sz, sx * 0.14 + (sx < 0 ? 0 : 0.55), sz + 0.25, 0.04, '#4d6b3a');
      c.P.cyl(0, 0.95, 0.07, 0.07, 0, 0.025, 14, '#cfc6b4'); c.P.disc(0, 0.95, 0.06, 0.026, 14, '#5d8f98');
      for (const x of [-1.05, 1.05]) for (const z of [0.7, 1.2]) c.P.cyl(x, z, 0.035, 0.002, 0.01, 0.17, 8, '#3d5a33');
      for (const k of ['A', 'B']) c.P.box(B[k].x0 + 0.08, 0.86, B[k].z0 + 0.1, B[k].x1 - 0.08, 0.94, B[k].z1 - 0.1, K.leavesPaint('#4d6b3a'), { ao: 0.9 });
      void b;
      return leisure({ x0: -1.55, x1: 1.55, top: 1.0, cast: [[-1.05, 0.2, 0.7], [1.05, 0.2, 0.7]], foot: [] });
    },
  ],
});

// ---------- Sternwarte: sandstone observatory hotel; copper domes grow with every stage, a planetarium dome in front ----------
export const sternwarte = (name) => ({
  name, atlas: [2048, 1536],
  stages: [
    (c) => {
      const S = { mat: 'plaster', tint: '#e5dccb', tile: 1.2, groundH: 0.3, floorH: 0.26, top: 0.08, pitch: 0.2, lit: c.lit, glowK: c.glowK, bands: true,
        win: { w: 0.08, h: 0.16, frame: '#f4efe4', mull: 'cross', ped: 'tri', pedColor: '#e2d6bd' }, groundWin: { w: 0.1, h: 0.18, frame: '#f4efe4', mull: 'cross', arch: true },
        plinth: { mat: 'sandstone', h: 0.3, tile: 0.45, tint: '#e2d3b6' }, doorArch: true, doorFrame: '#5b3a24' };
      c.st = { S, ring: true, trim: '#e2d6bd', roof: { kind: 'flat', spec: { mat: 'slate', tile: 0.5, tint: '#79818c' } } };
      const b = B.main, r = block(c, b, 1.0, c.st, { door: true, awn: [-0.5, 0.5], awnW: 0.16 });
      // one slim observatory tower over the middle of the block, a single copper dome on top
      const t = { x0: -0.2, x1: 0.2, z0: -0.62, z1: -0.28 };
      walls(c.P, t, 1.0, 1.75, { ...c.st.S, pitch: 0.2, groundH: 0.26, win: { ...c.st.S.win, w: 0.07 } }, { door: false });
      c.P.box(t.x0 - 0.03, 1.75, t.z0 - 0.03, t.x1 + 0.03, 1.79, t.z1 + 0.03, '#e2d6bd', { faces: 'px nx pz nz py' });
      dome(c.P, 0, -0.45, 0.17, 1.79, 0.05);
      sign(c, b, 0, 1.06, 0.95, 0.2); OP.flag(c.O, b.x0 + 0.06, b.z1 + 0.12, 0, 0.4); OP.flag(c.O, b.x1 - 0.06, b.z1 + 0.12, 0, 0.4);
      return { ...r, top: 2.05, cast: [...r.cast, [0, 2.0, -0.45], [t.x0, 1.8, t.z0], [t.x1, 1.8, t.z0]], scaf: scaf(b, 1.0) };
    },
    ...['A', 'B'].map((k) => (c) => {   // wings with a flat roof; the left carries a small white telescope dome, the right a weather mast
      const b = B[k], h = 0.72, r = block(c, b, h, { ...c.st, roof: { kind: 'flat', spec: { mat: 'slate', tile: 0.5, tint: '#79818c' } } }, { awn: [(b.x0 + b.x1) / 2], awnW: 0.14 });
      const cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
      if (k === 'A') dome(c.P, cx, cz, 0.15, h + 0.02, 0.06, '#efeae0');
      else { c.P.cyl(cx, cz, 0.012, 0.008, h, h + 0.5, 6, '#9aa0a3'); c.P.box(cx - 0.1, h + 0.38, cz - 0.006, cx + 0.1, h + 0.4, cz + 0.006, '#9aa0a3', { faces: 'pz py' }); }
      return { ...r, top: h + 0.55, cast: [...r.cast, [cx, h + 0.45, cz]], scaf: scaf(b, h) };
    }),
    ...['C', 'D'].map((k) => (c) => {   // slim rear towers carrying radio dishes
      const b = { x0: B[k].x0 + 0.1, x1: B[k].x1 - 0.1, z0: B[k].z0, z1: B[k].z1 - 0.1 }, h = 1.1, cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
      const r = block(c, b, h, { ...c.st, roof: { kind: 'flat' } }, { balc: false });
      c.P.cyl(cx, cz, 0.012, 0.01, h, h + 0.3, 6, '#9aa0a3'); c.P.cyl(cx, cz, 0.15, 0.015, h + 0.3, h + 0.38, 14, '#d8d4c8');
      return { ...r, top: h + 0.45, cast: [...r.cast, [cx, h + 0.4, cz]], scaf: scaf(b, h) };
    }),
    (c) => {   // the planetarium: a white dome on a low ring, steps and a paved circle
      const cx = 0, cz = 0.95;
      c.P.disc(cx, cz, 0.62, 0.01, 28, flat({ mat: 'sandstone', tile: 0.4, tint: '#efe6d2' }));
      c.P.cyl(cx, cz, 0.5, 0.5, 0.01, 0.1, 24, '#e9e1cf');
      c.P.mesh(new THREE.SphereGeometry(0.48, 24, 10, 0, Math.PI * 2, 0, Math.PI / 2).translate(cx, 0.1, cz), '#efeae0');
      c.P.box(-0.08, 0.1, cz + 0.4, 0.08, 0.2, cz + 0.56, '#e9e1cf');
      for (const x of [-0.95, 0.95]) K.lamp(c.P, x, 1.2, true);
      return leisure({ top: 0.7, cast: [[cx - 0.48, 0.1, cz], [cx + 0.48, 0.1, cz], [cx, 0.58, cz]], foot: [] });
    },
  ],
});

// ---------- Kurhaus: cream spa house, a portico of six columns, arcade wings, a rotunda pavilion, thermal bath ----------
export const kurhaus = (name) => ({
  name, atlas: [2048, 1024],
  stages: [
    (c) => {
      const S = { mat: 'plaster', tint: '#f1e9d6', tile: 1.2, groundH: 0.3, floorH: 0.26, top: 0.1, pitch: 0.2, lit: c.lit, glowK: c.glowK, bands: true,
        win: { w: 0.08, h: 0.16, frame: '#f7f3ea', mull: 'cross', ped: 'flat', pedColor: '#ece3cc' }, groundWin: { w: 0.1, h: 0.2, frame: '#f7f3ea', mull: 'cross', arch: true },
        plinth: { mat: 'sandstone', h: 0.05, tile: 0.4, tint: '#efe4cc' }, doorArch: true };
      const roof = { mat: 'slate', tile: 0.5, tint: '#a3a8ad' };
      c.st = { S, ring: true, trim: '#efe7d4', roof: { kind: 'hip', spec: roof, rise: 0.2, over: 0.04 } };
      const b = B.main, H = 1.12, r = block(c, b, H, c.st, { door: true });
      c.P.box(-0.14, H + 0.18, -0.56, 0.14, H + 0.3, -0.34, '#efe7d4'); c.P.box(-0.16, H + 0.3, -0.58, 0.16, H + 0.33, -0.32, '#8d9399', { faces: 'px nx pz nz py' });   // a roof lantern
      const pz = b.z1 + 0.2, py = 0.62;   // portico: six columns, a frieze, a pediment
      K.columns(c.P, -0.4, 0.4, 6, pz - 0.02, 0.02, py, 0.03, '#f4efe4');
      c.P.box(-0.48, 0, b.z1, 0.48, 0.02, pz + 0.04, '#d7cfbf');
      c.P.box(-0.47, py, b.z1, 0.47, py + 0.1, pz + 0.03, '#efe7d4', { faces: 'pz px nx py' });
      K.gable(c.P, { x0: -0.47, x1: 0.47, z0: b.z1, z1: pz + 0.03, y: py + 0.1 }, { rise: 0.16, over: 0.02, along: 'z', roof, end: '#efe7d4', fascia: '#efe7d4', ridge: '#8d9399' });
      OP.roofSign(c.O, 0, pz + 0.04, py + 0.12, 0.7, c.name, c.signLit, 0.13);
      OP.flag(c.O, -0.62, b.z1 + 0.22, 0, 0.4); OP.flag(c.O, 0.62, b.z1 + 0.22, 0, 0.4);
      return { ...r, top: H + 0.5, cast: [...r.cast, [0, H + 0.33, -0.45], [-0.47, py + 0.26, pz], [0.47, py + 0.26, pz]], scaf: scaf(b, H) };
    },
    ...['A', 'B'].map((k) => (c) => {   // wings with an arcade on the ground floor
      const b = B[k], h = 0.82, S = { ...c.st.S, pitch: 0.19, groundWin: { w: 0.12, h: 0.22, frame: '#f7f3ea', mull: 'none', arch: true } };
      const r = block(c, b, h, { ...c.st, roof: { ...c.st.roof, rise: 0.14, over: 0.03 } }, { S, awn: [(b.x0 + b.x1) / 2], awnW: 0.14 });
      return { ...r, scaf: scaf(b, r.top) };
    }),
    (c) => {   // the pump-room rotunda: a round pavilion under a copper cupola, behind the left wing
      const cx = (B.C.x0 + B.C.x1) / 2, cz = (B.C.z0 + B.C.z1) / 2, h = 0.7;
      c.P.cyl(cx, cz, 0.36, 0.36, 0, h, 18, (g, wu, hu) => { g.fillStyle = '#f1e9d6'; g.fillRect(-1, -1, wu + 2, hu + 2); for (let i = 0; i < 6; i++) windowAt(g, (wu * (i + 0.5)) / 6 - 0.045, hu * 0.3, 0.09, hu * 0.5, { frame: '#f7f3ea', mull: 'cross', arch: true, lit: i === 2 ? 1 : 0, glowK: c.glowK }); });
      c.P.cyl(cx, cz, 0.4, 0.4, h, h + 0.05, 18, '#efe7d4'); c.P.cyl(cx, cz, 0.38, 0.03, h + 0.05, h + 0.34, 18, COPPER);
      return { top: h + 0.4, cast: [[cx - 0.36, 0, cz], [cx + 0.36, 0, cz], [cx, h + 0.34, cz]], foot: [[cx - 0.36, cz], [cx + 0.36, cz]], scaf: scaf({ x0: cx - 0.36, x1: cx + 0.36, z0: cz - 0.36, z1: cz + 0.36 }, h) };
    },
    (c) => {   // the thermal bath: a big pool in a sandstone court, a colonnade behind it
      const r = pool(c.P, { x0: -1.3, x1: 1.3, z0: 0.55, z1: 1.45 }, { mat: 'sandstone', tile: 0.35, tint: '#efe6d2' }, '#efe7d4', 3);
      K.columns(c.P, -1.1, 1.1, 8, 0.6, 0.012, 0.3, 0.025, '#f4efe4');
      return leisure({ ...r, x0: -1.45, x1: 1.45 });
    },
  ],
});

// ---------- Leuchtturm: red-brick maritime hotel, steep gables, a striped lighthouse with a lit lantern, a viewing deck ----------
export const leuchtturm = (name) => ({
  name, atlas: [2048, 1024],
  stages: [
    (c) => {
      const S = { mat: 'brick', tile: 0.5, groundH: 0.26, floorH: 0.24, top: 0.06, pitch: 0.2, lit: c.lit, glowK: c.glowK,
        win: { w: 0.085, h: 0.14, frame: '#f7f5ef', mull: 'cross', sillColor: '#f0ece2' }, groundWin: { w: 0.12, h: 0.16, frame: '#f7f5ef', mull: 'cross' },
        plinth: { mat: 'stone', h: 0.05, tile: 0.4, tint: '#9a9a9a' }, bandColor: 'rgba(240,236,226,0.85)' };
      c.st = { S, trim: '#f4f1ea', roof: { kind: 'gablex', spec: { mat: 'clay', tile: 0.45, tint: '#7b5a50' }, rise: 0.42, over: 0.05, fascia: '#f4f1ea', ridge: '#5e3f36' }, dormers: [-0.4, 0.4] };
      const b = B.main, r = block(c, b, 0.98, c.st, { door: true, awn: [-0.5, -0.17, 0.17], awnW: 0.13 });
      sign(c, b, 0, 0.19, 0.62, 0.09); OP.flag(c.O, b.x0 + 0.08, b.z1 + 0.2, 0, 0.42);
      return { ...r, scaf: scaf(b, r.top) };
    },
    ...['A', 'B'].map((k) => (c) => {
      const b = B[k], r = block(c, b, 0.74, { ...c.st, roof: { ...c.st.roof, kind: 'gablez', rise: 0.3 }, dormers: [] }, { awn: [(b.x0 + b.x1) / 2], awnW: 0.14 });
      return { ...r, scaf: scaf(b, r.top) };
    }),
    (c) => {   // the lighthouse beside the right wing: stripes, gallery, a glowing lantern, a red cap
      const tx = 1.62, tz = 0.1, th = 1.75;
      c.P.cyl(tx, tz, 0.17, 0.13, 0, th, 16, (g, wu, hu) => { for (let i = 0; i < 6; i++) { g.fillStyle = i % 2 ? '#f2eee6' : '#b8392c'; g.fillRect(-0.1, (hu * i) / 6 - 0.01, wu + 0.2, hu / 6 + 0.02); } for (let i = 1; i < 4; i++) windowAt(g, wu * 0.12, (hu * i) / 4, 0.04, 0.06, { frame: '#333', mull: 'none', sill: false, lit: 0 }); });
      c.P.cyl(tx, tz, 0.2, 0.2, th, th + 0.025, 16, '#2f3236', { cap: '#3b3e42' });
      c.P.cyl(tx, tz, 0.095, 0.095, th + 0.025, th + 0.15, 12, (g, wu, hu) => { g.fillStyle = '#20242a'; g.fillRect(-1, -1, wu + 2, hu + 2); g.fillStyle = '#fff1c4'; g.fillRect(-1, hu * 0.12, wu + 2, hu * 0.76); glow(g, () => g.fillRect(-1, hu * 0.12, wu + 2, hu * 0.76), c.lamp); g.fillStyle = '#20242a'; for (let i = 0; i < 8; i++) g.fillRect((wu * i) / 8, 0, wu * 0.02, hu); });
      c.P.cyl(tx, tz, 0.11, 0.01, th + 0.15, th + 0.24, 12, '#b8392c');
      const bb = { x0: tx - 0.17, x1: tx + 0.17, z0: tz - 0.17, z1: tz + 0.17 };
      return { top: th + 0.26, cast: [[tx - 0.17, 0, tz], [tx + 0.17, 0, tz], [tx, th + 0.24, tz]], foot: [[tx - 0.17, tz], [tx + 0.17, tz]], scaf: scaf(bb, th) };
    },
    (c) => {   // the viewing deck: timber boards, a rail round the edge, loungers and a telescope
      const b = B.L; slab(c.P, b, 0.03, deck(0.4)); c.P.box(b.x0, 0, b.z0, b.x1, 0.03, b.z1, '#7a6048', { faces: 'pz px nx' });
      c.P.wall(b.x0, b.z1, b.x1, b.z1, 0.03, 0.14, K.railing('#e9e4d8', '#6e6a62')); c.P.wall(b.x1, b.z1, b.x1, b.z0, 0.03, 0.14, K.railing('#e9e4d8', '#6e6a62'));
      for (let i = 0; i < 4; i++) K.lounger(c.P, b.x0 + 0.2 + i * 0.14, b.z0 + 0.25, 0);
      c.P.cyl(0.6, 1.0, 0.012, 0.01, 0.03, 0.2, 6, '#2c2c2c'); c.P.box(0.56, 0.2, 0.96, 0.7, 0.23, 1.04, '#2c2c2c');
      return leisure({ top: 0.3, cast: [], foot: [] });
    },
  ],
});
export { corners };
