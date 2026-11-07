// Four hotels, each its own architecture and built in stages (main building, wings, a leisure facility): the
// Kastanienhof inn, the Seeblick lake hotel, the Waldhaus chalet and the Alpenrose mountain hotel. Our own designs.
import { block, slab, pool, deck, SLOT as B, corners } from './hotel-kit.mjs';
import { flat } from './paint.mjs';
import * as K from './props.mjs';
import * as OP from './owner-props.mjs';

const scaf = (b, top) => ({ box: b, h: top - 0.1 });
const leisure = (extra) => ({ axis: 'x', x0: B.L.x0 - 0.15, x1: B.L.x1 + 0.15, ...extra });
const sign = (c, b, x, y, w, h) => OP.roofSign(c.O, x, b.z1 + 0.02, y, w, c.name, c.signLit, h);

// ---------- Kastanienhof: half-timbered inn, barn wing, beer garden under chestnuts ----------
export const kastanienhof = (name) => ({
  name, atlas: [2048, 1024],
  stages: [
    (c) => {
      const S = { mat: 'plaster', tint: '#f2ede2', tile: 1, groundH: 0.27, floorH: 0.24, top: 0.04, pitch: 0.22, lit: c.lit, glowK: c.glowK, cornice: false,
        win: { w: 0.08, h: 0.12, frame: '#f4efe6', mull: 'cross', shutters: '#5a3b26' }, groundWin: { w: 0.1, h: 0.13, frame: '#e9e2d4', mull: 'cross' },
        plinth: { mat: 'stone', h: 0.27, tile: 0.35, tint: '#c9bfae' }, fachwerk: { color: '#3b2a1e', beam: 0.02 }, doorFrame: '#4a3020', doorArch: true };
      c.st = S;
      const b = B.main, r = block(c, b, 0.77, { S, trim: '#f2ede2' }, { door: true, awn: [-0.5, 0.5], awnW: 0.17, chimney: [-0.3, -0.35, 1.05, 1.5, '#8a5a44'], dormers: [-0.4, 0, 0.4],
        roof: { kind: 'gablex', rise: 0.58, over: 0.06, spec: { mat: 'clay', tile: 0.4 }, fascia: '#3b2a1e', ridge: '#6d3a26' } });
      sign(c, b, 0.05, 0.29, 0.86, 0.14); OP.flag(c.O, b.x1 - 0.06, b.z1 + 0.16, 0, 0.38);
      return { ...r, scaf: scaf(b, r.top) };
    },
    (c) => {
      const S = { ...c.st, mat: 'timber', tint: '#9a7a5c', tile: 0.4, fachwerk: null, pitch: 0.24 };
      const b = B.A, r = block(c, b, 0.55, { S, trim: '#f2ede2' }, { roof: { kind: 'gablez', rise: 0.42, over: 0.05, spec: { mat: 'clay', tile: 0.4 }, fascia: '#3b2a1e', ridge: '#6d3a26', endSpec: S } });
      OP.awning(c.O, 0, b.z1, 0.22, 0.17);
      return { ...r, scaf: scaf(b, r.top) };
    },
    (c) => {   // the beer garden: gravel court, long tables under three chestnuts
      const b = B.L, y = 0.008;
      slab(c.P, b, y, flat({ mat: 'gravel', tile: 0.5, tint: '#f1e9d6' })); c.P.box(b.x0, 0, b.z0, b.x1, y, b.z1, '#8f8a7c', { faces: 'pz px nx' });
      for (const [x, z, s] of [[-0.8, 0.8, 0.66], [0.1, 0.72, 0.74], [0.85, 0.85, 0.64]]) K.tree(c.P, x, z, s, c.leaves);
      for (let i = 0; i < 4; i++) { const x = -0.7 + i * 0.45; c.P.box(x - 0.1, 0.03, 1.16, x + 0.1, 0.036, 1.22, '#8a6440'); K.bench(c.P, x, 1.09, 0.2); K.bench(c.P, x, 1.29, 0.2); }
      for (const x of [-0.45, 0.5]) K.parasol(c.P, x, 1.25, '#efe7d6', 0.12);
      return leisure({ top: 1.1, cast: [[-0.8, 0.9, 0.8], [0.1, 1, 0.72], [0.85, 0.9, 0.85]], foot: [] });
    },
  ],
});

// ---------- Seeblick: white lake hotel, a dark-wood balcony on every floor, slate hip roofs, deck and jetty ----------
export const seeblick = (name) => ({
  name, atlas: [2048, 1024],
  stages: [
    (c) => {
      const S = { mat: 'plaster', tint: '#efece4', tile: 1.2, groundH: 0.27, floorH: 0.23, top: 0.06, pitch: 0.19, lit: c.lit, glowK: c.glowK,
        win: { w: 0.085, h: 0.14, frame: '#f6f3ec', mull: 'cross' }, groundWin: { w: 0.13, h: 0.17, frame: '#3a2e26', mull: 'cross' }, plinth: { mat: 'stone', h: 0.04, tile: 0.4 } };
      c.st = { S, trim: '#f2efe8', roof: { kind: 'hip', spec: { mat: 'slate', tile: 0.5, tint: '#8a8f99' }, over: 0.06, rise: 0.3 }, balc: { floors: [0.27, 0.5, 0.73, 0.96], rail: K.boards('#6b4a32'), slab: '#e2ddd2' } };
      const b = B.main, r = block(c, b, 1.21, c.st, { door: true, awn: [-0.5, -0.17, 0.17, 0.5] });
      sign(c, b, 0, 1.27, 1.0, 0.26); OP.flag(c.O, b.x0 + 0.1, b.z1 + 0.18, 0, 0.42); OP.flag(c.O, b.x1 - 0.1, b.z1 + 0.18, 0, 0.42);
      return { ...r, scaf: scaf(b, r.top) };
    },
    ...['A', 'B'].map((k) => (c) => {
      const b = B[k], r = block(c, b, 0.73, { ...c.st, roof: { ...c.st.roof, rise: 0.22 }, balc: { ...c.st.balc, floors: [0.27, 0.5] } }, { awn: [(b.x0 + b.x1) / 2], awnW: 0.2 });
      return { ...r, scaf: scaf(b, r.top) };
    }),
    (c) => {   // the lake terrace: timber deck, loungers and a jetty running out into the water
      const b = B.L; slab(c.P, b, 0.02, deck(0.4)); c.P.box(b.x0, 0, b.z0, b.x1, 0.02, b.z1, '#7a6048', { faces: 'pz px nx' });
      c.P.box(-2.3, 0.012, 0.95, b.x0, 0.03, 1.12, deck(0.3), { faces: 'py pz nz' });
      for (const x of [-2.2, -1.9, -1.6, -1.3]) for (const z of [0.97, 1.1]) c.P.cyl(x, z, 0.012, 0.012, -0.06, 0.03, 5, '#4b3a2c');
      for (let i = 0; i < 5; i++) K.lounger(c.P, b.x0 + 0.15 + i * 0.12, 1.2, 0);
      c.P.box(-2.45, 0.0, 1.18, -2.15, 0.06, 1.3, '#7a5638'); c.P.box(-2.43, 0.06, 1.2, -2.17, 0.075, 1.28, '#efe7d6', { faces: 'py' });   // a rowing boat at the jetty
      for (const x of [-0.5, 0.2, 0.8]) K.parasol(c.P, x, 0.9, '#efe7d6', 0.11);
      return leisure({ x0: -2.35, top: 0.2, cast: [], foot: [] });
    },
  ],
});

// ---------- Waldhaus: chalet, rubble-stone ground floor, larch boarding, green shutters, flower balconies ----------
export const waldhaus = (name) => ({
  name, atlas: [2048, 1024],
  stages: [
    (c) => {
      const S = { mat: 'timber', tint: '#c79a6c', tile: 0.45, groundH: 0.28, floorH: 0.25, top: 0.03, pitch: 0.2, lit: c.lit, glowK: c.glowK, cornice: false,
        win: { w: 0.075, h: 0.12, frame: '#efe6d2', mull: 'cross', shutters: '#4d6b45' }, groundWin: { w: 0.09, h: 0.12, frame: '#efe6d2', mull: 'cross', shutters: '#4d6b45' },
        plinth: { mat: 'stone', h: 0.28, tile: 0.35, tint: '#d8d0c2' }, doorFrame: '#5a3b26' };
      c.st = { S, trim: '#5a3b26', roof: { kind: 'gablez', spec: { mat: 'slate', tile: 0.45, tint: '#7a6658' }, over: 0.15, rise: 0.34, fascia: '#5a3b26', ridge: '#4e3f36' }, balc: { floors: [0.28, 0.53], rail: K.boards('#8a5a34', '#d2332f'), slab: '#7a5638' } };
      const b = B.main, r = block(c, b, 0.8, c.st, { door: true, awn: [-0.5, 0.5], awnW: 0.12 });
      sign(c, b, 0, 0.62, 0.8, 0.14); OP.flag(c.O, b.x0 - 0.04, b.z1 + 0.25, 0, 0.4);
      return { ...r, scaf: scaf(b, r.top) };
    },
    ...['A', 'B'].map((k) => (c) => {
      const b = B[k], r = block(c, b, 0.55, { ...c.st, roof: { ...c.st.roof, over: 0.1, rise: 0.26 }, balc: { ...c.st.balc, floors: [0.28] } });
      return { ...r, scaf: scaf(b, r.top) };
    }),
    (c) => {   // the sauna house: a log hut with a chimney, a wooden hot tub and firs
      const hut = { x0: -0.55, x1: 0.1, z0: 0.7, z1: 1.2 }, S = { mat: 'timber', tint: '#a9774a', tile: 0.3, groundH: 0.2, floorH: 0.2, top: 0.02, pitch: 0.3, lit: c.lit, glowK: c.glowK, cornice: false, win: { w: 0.06, h: 0.08, frame: '#efe6d2', mull: 'none' } };
      slab(c.P, B.L, 0.008, flat({ mat: 'grass', tile: 0.5, tint: '#9db07a' }));
      block(c, hut, 0.2, { S, roof: { kind: 'gablez', spec: { mat: 'slate', tile: 0.4, tint: '#6a5848' }, rise: 0.17, over: 0.06, fascia: '#5a3b26' } }, { door: true, balc: false });
      K.chimney(c.P, -0.4, 0.85, 0.22, 0.55, '#6d5f55');
      c.P.cyl(0.55, 1.0, 0.11, 0.1, 0, 0.08, 14, '#a9774a', { cap: '#6fa3b0' });
      for (const [x, z, s] of [[-0.95, 0.85, 0.5], [-0.85, 1.2, 0.42], [0.95, 0.8, 0.55], [0.8, 1.25, 0.4], [0.2, 0.7, 0.34]]) K.conifer(c.P, x, z, s, c.conifer);
      return leisure({ top: 0.7, cast: [[-0.95, 0.5, 0.85], [0.95, 0.55, 0.8]], foot: [] });
    },
  ],
});

// ---------- Alpenrose: white mountain hotel, wide timber balconies, big overhanging roofs, stepped wings, terraced spa ----------
export const alpenrose = (name) => ({
  name, atlas: [2048, 1024],
  stages: [
    (c) => {
      const S = { mat: 'plaster', tint: '#f4eee2', tile: 1, groundH: 0.28, floorH: 0.24, top: 0.04, pitch: 0.21, lit: c.lit, glowK: c.glowK, cornice: false,
        win: { w: 0.078, h: 0.125, frame: '#5a3b26', mull: 'cross', shutters: '#a8352c' }, groundWin: { w: 0.1, h: 0.14, frame: '#5a3b26', mull: 'cross' },
        plinth: { mat: 'stone', h: 0.12, tile: 0.35, tint: '#cfc6b6' }, doorFrame: '#5a3b26', doorArch: true };
      c.st = { S, trim: '#5a3b26', roof: { kind: 'gablez', spec: { mat: 'clay', tile: 0.45, tint: '#8a5a48' }, over: 0.2, rise: 0.4, fascia: '#5a3b26', ridge: '#5e3f36', endSpec: { ...S, mat: 'timber', tint: '#a07a54', tile: 0.4 } },
        balc: { floors: [0.28, 0.52, 0.76], rail: K.boards('#6b4a32', '#c8392f'), slab: '#7a5638' } };
      const b = B.main, r = block(c, b, 1.0, c.st, { door: true, awn: [-0.45, 0.45], awnW: 0.15, chimney: [0.4, -0.3, 1.1, 1.6, '#8a8078'] });
      sign(c, b, 0, 0.84, 0.9, 0.15); OP.flag(c.O, 0, b.z1 + 0.05, 1.25, 0.34); OP.flag(c.O, b.x1 - 0.04, b.z1 + 0.2, 0, 0.42);
      return { ...r, scaf: scaf(b, r.top) };
    },
    ...['A', 'B'].map((k) => (c) => {
      const b = B[k], r = block(c, b, 0.62, { ...c.st, roof: { ...c.st.roof, over: 0.14, rise: 0.3 }, balc: { ...c.st.balc, floors: [0.28, 0.5] } }, { awn: [(b.x0 + b.x1) / 2], awnW: 0.16 });
      return { ...r, scaf: scaf(b, r.top) };
    }),
    (c) => {   // the spa garden: stone terraces round a warm pool, loungers, firs
      const b = B.L; slab(c.P, { x0: -1.4, x1: 1.4, z0: 0.55, z1: 1.45 }, 0.006, flat({ mat: 'grass', tint: '#aab87f', tile: 0.5 }));
      const r = pool(c.P, { x0: -0.95, x1: 0.95, z0: 0.62, z1: 1.38 }, { mat: 'stone', tile: 0.4, tint: '#cfc6b6' }, '#efe7d6', 2);
      for (const [x, z, s] of [[-1.25, 0.8, 0.5], [-1.1, 1.25, 0.42], [1.25, 0.85, 0.55], [1.1, 1.3, 0.4]]) K.conifer(c.P, x, z, s, c.conifer);
      void b;
      return leisure({ ...r, x0: -1.45, x1: 1.45, cast: [[-1.25, 0.5, 0.8], [1.25, 0.55, 0.85]] });
    },
  ],
});
export { corners };
