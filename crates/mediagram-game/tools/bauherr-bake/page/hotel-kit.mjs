// What every hotel is made of: a lot with six building slots (main building, two wings, two rear blocks, the leisure
// facility in front), a generic block builder (walls, roof, balconies, awnings), and the frame that builds a hotel's
// stages into one atlas and records each stage's index range, height and shadow casters.
// Lot-local units: x across the front, z towards the road; SCALE turns them into world units.
import { Atlas, Part } from './atlas.mjs';
import { facade, flat, plaster, material, windowAt, poolWater } from './paint.mjs';
import { rand } from './util.mjs';
import * as K from './props.mjs';
import * as OP from './owner-props.mjs';

export const SCALE = 2.0;
export const SLOT = {
  main: { x0: -0.78, x1: 0.78, z0: -0.95, z1: 0.1 },
  A: { x0: -1.5, x1: -0.78, z0: -0.55, z1: 0.45 },
  B: { x0: 0.78, x1: 1.5, z0: -0.55, z1: 0.45 },
  C: { x0: -1.5, x1: -0.78, z0: -1.45, z1: -0.6 },
  D: { x0: 0.78, x1: 1.5, z0: -1.45, z1: -0.6 },
  L: { x0: -1.05, x1: 1.05, z0: 0.6, z1: 1.4 },
};
// weathered timber boards, lightened: the deck photo is a dark oak
export const deck = (tile = 0.4) => (g, wu, hu) => { material(g, { mat: 'deck', tile }, -0.05, -0.05, wu + 0.1, hu + 0.1); g.fillStyle = 'rgba(236,206,160,0.42)'; g.fillRect(-0.05, -0.05, wu + 0.1, hu + 0.1); };
export const corners = (b, y) => [[b.x0, y, b.z0], [b.x1, y, b.z0], [b.x1, y, b.z1], [b.x0, y, b.z1]];

// four walls of a block, front with the door; o.side for different side walls
export function walls(P, b, y0, y1, spec, o = {}) {
  P.wall(b.x0, b.z1, b.x1, b.z1, y0, y1, facade(spec, { door: o.door ?? true }), { ppu: o.ppu });
  P.wall(b.x1, b.z1, b.x1, b.z0, y0, y1, facade(o.side ?? spec, {}), { ppu: o.ppu }); P.wall(b.x0, b.z0, b.x0, b.z1, y0, y1, facade(o.side ?? spec, {}), { ppu: o.ppu });
  P.wall(b.x1, b.z0, b.x0, b.z0, y0, y1, facade(o.side ?? spec, {}), { ppu: o.ppu });
}
export const gableEnd = (spec) => (g, wu, hu) => {
  if (spec.mat === 'plaster') plaster(g, -0.1, -0.1, wu + 0.2, hu + 0.2, spec.tint); else material(g, spec, -0.1, -0.1, wu + 0.2, hu + 0.2);
  if (hu > 0.2) windowAt(g, wu / 2 - 0.04, hu * 0.45, 0.08, 0.1, { frame: spec.win?.frame ?? '#eee', mull: 'cross', sill: false, lit: 0 });
};
export const dormerWin = (trim, lit) => (g, wu, hu) => { g.fillStyle = trim; g.fillRect(-1, -1, wu + 2, hu + 2); windowAt(g, wu * 0.18, hu * 0.16, wu * 0.64, hu * 0.7, { frame: '#f2efe8', mull: 'cross', sill: false, lit: rand() < lit ? 1 : 0 }); };

// A building block: walls, a roof, balconies, awnings and dormers, from the hotel's style st.
// st: { S, side, roof: { kind: hip|gablex|gablez|mansard|flat, spec, rise, over, fascia, ridge, ... }, trim, balc: { floors, rail, slab }, awn: [x], dormers: [x] }
export function block(c, b, h, st, o = {}) {
  const { P, O } = c, r = { ...st.roof, ...o.roof }, trim = st.trim ?? '#e9e3d6';
  walls(P, b, 0, h, o.S ?? st.S, { door: o.door ?? false, side: o.side ?? st.side });
  if (st.ring) K.ring(P, b, h - 0.03, 0.04, 0.03, trim);
  const rb = { ...b, y: h };
  if (r.kind === 'hip') K.hip(P, rb, { rise: r.rise, over: r.over ?? 0.05, roof: r.spec, fascia: r.fascia ?? trim });
  else if (r.kind === 'gablex' || r.kind === 'gablez') K.gable(P, rb, { rise: r.rise, over: r.over ?? 0.05, along: r.kind === 'gablex' ? 'x' : 'z', roof: r.spec, end: gableEnd(r.endSpec ?? st.S), fascia: r.fascia ?? trim, ridge: r.ridge });
  else if (r.kind === 'mansard') K.mansard(P, rb, { roof: r.spec, roofUpper: r.upper ?? r.spec, lower: r.lower ?? 0.28, inset: 0.11, upper: r.rise ?? 0.08, dormers: o.dormers ?? st.dormers ?? [], dormerWin: dormerWin(trim, 0.12), trim });
  else { P.box(b.x0, h, b.z0, b.x1, h + 0.012, b.z1, flat(r.spec ?? { mat: 'slate', tile: 0.5 }), { faces: 'py' }); K.ring(P, b, h, 0.05, 0.015, trim); }
  if (r.kind === 'gablex' || r.kind === 'gablez') for (const x of o.dormers ?? st.dormers ?? []) K.dormer(P, x, b.z1 - 0.1, h + 0.06, { roof: r.spec, trim, dormerWin: dormerWin(trim, 0.12), ridgeColor: r.ridge });
  for (const f of st.balc?.floors ?? []) if (o.balc !== false) K.balcony(P, b.x0 + 0.04, b.x1 - 0.04, b.z1, f, 0.07, st.balc.rail, st.balc.slab);
  for (const x of o.awn ?? []) OP.awning(O, x, b.z1, o.awnY ?? st.awnY ?? 0.25, o.awnW ?? 0.15);
  if (o.chimney) K.chimney(P, ...o.chimney);
  const rise = r.kind === 'flat' ? 0.05 : r.kind === 'mansard' ? (r.lower ?? 0.28) + (r.rise ?? 0.08) : r.rise;
  const mx = (b.x0 + b.x1) / 2, mz = (b.z0 + b.z1) / 2;
  const peak = r.kind === 'gablex' ? [[b.x0, h + rise, mz], [b.x1, h + rise, mz]] : r.kind === 'gablez' ? [[mx, h + rise, b.z0], [mx, h + rise, b.z1]] : [[mx, h + rise, mz]];
  return { top: h + rise + 0.03, cast: [...corners(b, 0), ...corners(b, h), ...peak], foot: corners(b, 0).map(([x, , z]) => [x, z]) };
}

// the leisure facilities
export function slab(P, b, y, fill) { P.poly([[b.x0, y, b.z1], [b.x1, y, b.z1], [b.x1, y, b.z0], [b.x0, y, b.z0]], fill, { ao: 0.95 }); }
export function pool(P, b, deck, color = '#e9e4d8', n = 3) {
  const w = b.x1 - b.x0, d = b.z1 - b.z0, cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2, pw = w * 0.62, pd = d * 0.5, y = 0.012;
  slab(P, b, y, flat(deck)); P.box(b.x0, 0, b.z0, b.x1, y, b.z1, '#b9b2a4', { faces: 'pz px nx' });
  const wb = { x0: cx - pw / 2, x1: cx + pw / 2, z0: cz - pd / 2, z1: cz + pd / 2 }; slab(P, wb, y + 0.002, poolWater);
  for (const s of [-1, 1]) K.box4(P, cx + s * (pw / 2 + 0.008), wb.z0, cx + s * (pw / 2 + 0.004), wb.z1, y, y + 0.005, '#e9e4d8');
  for (let i = 0; i < 4; i++) { K.lounger(P, b.x0 + 0.07 + i * 0.055, b.z0 + 0.08, 0); K.lounger(P, b.x1 - 0.07 - i * 0.055, b.z0 + 0.08, 0); }
  for (let i = 0; i < n; i++) K.parasol(P, b.x0 + 0.1 + (i * (w - 0.2)) / Math.max(1, n - 1), b.z1 - 0.07, color);
  return { top: 0.2, cast: [[b.x0, 0.16, b.z1], [b.x1, 0.16, b.z1]], foot: [] };
}

/**
 * Builds a hotel into its own atlas. def: { name, stages: [(c) => { top, cast?, foot?, scaf?, axis? }] }.
 * Index order in the base part: for-sale group, raw lot ground, stage 1..n, scaffolds; in the owner part: property
 * line and plot sign, then each stage's owner pieces. Heights are returned in world units.
 */
export function buildHotel(def, lot, light, look, atlasSize = [2048, 1024]) {
  const A = new Atlas(atlasSize[0], atlasSize[1], 240), place = { rot: lot.rot, x: lot.x, z: lot.z, scale: SCALE };
  const P = new Part(A, light, place), O = new Part(A, light, { ...place, tint: 1 });
  const c = { P, O, A, name: def.name, lit: look.lit, glowK: look.glowK, signLit: look.signLit, lamp: look.lamp, leaves: A.face(1, 1, K.leavesPaint(look.leaves), 256), conifer: A.face(1, 1, K.leavesPaint(look.firs), 256) };
  const hw = lot.w / SCALE / 2, hd = lot.d / SCALE / 2;
  const span = (part, fn) => { const s = part.mark; const r = fn(); return { range: [s, part.mark - s], r }; };
  const sale = span(P, () => forSaleSign(P, def.name, hd)).range;
  const overlay = span(P, () => { const m = hw - 0.12, n = hd - 0.12; slab(P, { x0: -m, x1: m, z0: -n, z1: n }, 0.012, rawGround(m, n)); siteProps(P, m, n); }).range;
  const own0 = span(O, () => { OP.propertyLine(O, hw * 2 - 0.1, hd * 2 - 0.1); OP.plotSign(O, -0.9, hd - 0.2, def.name); }).range;
  const stages = def.stages.map((build) => {
    const po = O.mark, p = span(P, () => build(c));
    return { ...p.r, p: p.range, o: [po, O.mark - po] };
  });
  stages.forEach((s) => { if (s.scaf) s.scafRange = span(P, () => K.scaffold(P, s.scaf.box, s.scaf.h, 0.2)).range; });
  return { A, P, O, sale, overlay, own0, stages, hw, hd };
}

// the board of a lot for sale: a blue and white estate sign facing the road
function forSaleSign(P, name, hd) {
  const x = 0, z = hd - 0.25, board = (g, wu, hu) => {
    g.fillStyle = '#f4f0e6'; g.fillRect(-0.1, -0.1, wu + 0.2, hu + 0.2); g.fillStyle = '#2f5d8a'; g.fillRect(-0.1, -0.1, wu + 0.2, hu * 0.34 + 0.1);
    const s = wu / 300; g.save(); g.scale(s, s); const H = hu / s; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillStyle = '#fff'; g.font = `800 ${H * 0.24}px "Bricolage Grotesque", sans-serif`; g.fillText('Zu verkaufen', 150, H * 0.17, 280);
    g.fillStyle = '#2a2f3a'; g.font = `800 ${H * 0.3}px "Bricolage Grotesque", sans-serif`; g.fillText(name, 150, H * 0.62, 280); g.restore();
  };
  for (const s of [-0.34, 0.34]) P.box(x + s - 0.012, 0, z - 0.012, x + s + 0.012, 0.52, z + 0.012, '#e9e4d8');
  P.box(x - 0.46, 0.26, z + 0.012, x + 0.46, 0.56, z + 0.03, '#e9e4d8', { front: board, faces: 'pz px nx py' });
}
// a lot not yet built on: raw gravel, a surveyor's string with stakes
const rawGround = (m, n) => (g, wu, hu) => {
  material(g, { mat: 'gravel', tile: 0.8 }, -0.05, -0.05, wu + 0.1, hu + 0.1);
  g.fillStyle = 'rgba(205,205,208,0.42)'; g.fillRect(-0.05, -0.05, wu + 0.1, hu + 0.1);
  g.strokeStyle = 'rgba(250,250,240,0.8)'; g.lineWidth = 0.012; g.strokeRect(0.1, 0.1, wu - 0.2, hu - 0.2);
  g.fillStyle = '#d9cfb8'; for (const [a, b] of [[0.1, 0.1], [wu - 0.1, 0.1], [wu - 0.1, hu - 0.1], [0.1, hu - 0.1]]) { g.beginPath(); g.arc(a, b, 0.03, 0, 7); g.fill(); }
};
// what a building site has lying about: a cabin, pallets of bricks, a heap of sand, traffic cones
function siteProps(P, m, n) {
  P.box(-m + 0.15, 0.012, -n + 0.15, -m + 0.7, 0.2, -n + 0.45, '#6f8aa0'); P.box(-m + 0.13, 0.2, -n + 0.13, -m + 0.72, 0.215, -n + 0.47, '#e6e1d6', { faces: 'px nx py pz nz' });
  for (const [x, z] of [[m - 0.5, -n + 0.3], [m - 0.25, -n + 0.45]]) { P.box(x - 0.11, 0.012, z - 0.07, x + 0.11, 0.04, z + 0.07, '#a07a4a'); P.box(x - 0.1, 0.04, z - 0.065, x + 0.1, 0.12, z + 0.065, '#a65a43'); }
  P.cyl(0.2, -n + 0.35, 0.26, 0.02, 0.012, 0.2, 10, '#d8c79a');
  for (const x of [-0.7, 0, 0.7]) P.cyl(x, n - 0.2, 0.03, 0.008, 0.012, 0.09, 6, '#e8742a');
}
