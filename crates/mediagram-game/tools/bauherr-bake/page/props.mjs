// Geometry pieces built into a Part (see real-atlas.js): roofs, trim, balconies, columns, awnings, flags, signs,
// scaffolding, a tower crane, trees, lamps and the small things round a hotel. Part-local units (1 ≈ 13 m).
import * as THREE from 'three';
import { mergeVertices } from 'three/addons/utils/BufferGeometryUtils.js';
import { rand } from './util.mjs';
import { roof as roofPaint, flat, stripes, sign as signPaint, glow, material, photo, over } from './paint.mjs';

// ---------- roofs over the box x0..x1 × z0..z1 with eaves at y ----------
export function gable(P, b, o) {   // o: { rise, over, along: 'x'|'z', roof, end }: end paints the gable walls
  const { x0, x1, z0, z1, y } = b, ov = o.over ?? 0.05, r = o.rise, ye = y - ov * 0.4, fa = o.fascia ?? '#4a3b30';
  if ((o.along ?? 'x') === 'x') {
    const zm = (z0 + z1) / 2, yr = y + r;
    P.poly([[x0 - ov, ye, z1 + ov], [x1 + ov, ye, z1 + ov], [x1 + ov, yr, zm], [x0 - ov, yr, zm]], roofPaint(o.roof));
    P.poly([[x1 + ov, ye, z0 - ov], [x0 - ov, ye, z0 - ov], [x0 - ov, yr, zm], [x1 + ov, yr, zm]], roofPaint(o.roof));
    P.poly([[x1, y, z1], [x1, y, z0], [x1, yr - 0.01, zm]], o.end); P.poly([[x0, y, z0], [x0, y, z1], [x0, yr - 0.01, zm]], o.end);
    P.wall(x0 - ov, z1 + ov, x1 + ov, z1 + ov, ye - 0.025, ye, fa); P.wall(x1 + ov, z0 - ov, x0 - ov, z0 - ov, ye - 0.025, ye, fa);
    P.box(x0 - ov, yr - 0.01, zm - 0.018, x1 + ov, yr + 0.022, zm + 0.018, o.ridge ?? '#5a3a2c');
  } else {
    const xm = (x0 + x1) / 2, yr = y + r;
    P.poly([[x1 + ov, ye, z1 + ov], [x1 + ov, ye, z0 - ov], [xm, yr, z0 - ov], [xm, yr, z1 + ov]], roofPaint(o.roof));
    P.poly([[x0 - ov, ye, z0 - ov], [x0 - ov, ye, z1 + ov], [xm, yr, z1 + ov], [xm, yr, z0 - ov]], roofPaint(o.roof));
    P.poly([[x0, y, z1], [x1, y, z1], [xm, yr - 0.01, z1]], o.end); P.poly([[x1, y, z0], [x0, y, z0], [xm, yr - 0.01, z0]], o.end);
    P.poly([[x0 - ov, ye - 0.03, z1 + ov], [xm, yr - 0.03, z1 + ov], [xm, yr, z1 + ov], [x0 - ov, ye, z1 + ov]], fa);   // barge boards
    P.poly([[xm, yr - 0.03, z1 + ov], [x1 + ov, ye - 0.03, z1 + ov], [x1 + ov, ye, z1 + ov], [xm, yr, z1 + ov]], fa);
    P.box(xm - 0.018, yr - 0.01, z0 - ov, xm + 0.018, yr + 0.022, z1 + ov, o.ridge ?? '#5a3a2c');
  }
}
export function hip(P, b, o) {   // o: { rise, over, roof }
  let { x0, x1, z0, z1, y } = b; const ov = o.over ?? 0.04, ye = y - ov * 0.4;
  x0 -= ov; x1 += ov; z0 -= ov; z1 += ov;
  const yr = y + o.rise, R = roofPaint(o.roof), fa = o.fascia ?? '#e9e3d6';
  if (x1 - x0 >= z1 - z0) {
    const zm = (z0 + z1) / 2, k = Math.min((z1 - z0) / 2, (x1 - x0) / 2 - 0.001);
    P.poly([[x0, ye, z1], [x1, ye, z1], [x1 - k, yr, zm], [x0 + k, yr, zm]], R); P.poly([[x1, ye, z0], [x0, ye, z0], [x0 + k, yr, zm], [x1 - k, yr, zm]], R);
    P.poly([[x1, ye, z1], [x1, ye, z0], [x1 - k, yr, zm]], R); P.poly([[x0, ye, z0], [x0, ye, z1], [x0 + k, yr, zm]], R);
  } else {
    const xm = (x0 + x1) / 2, k = Math.min((x1 - x0) / 2, (z1 - z0) / 2 - 0.001);
    P.poly([[x1, ye, z1], [x1, ye, z0], [xm, yr, z0 + k], [xm, yr, z1 - k]], R); P.poly([[x0, ye, z0], [x0, ye, z1], [xm, yr, z1 - k], [xm, yr, z0 + k]], R);
    P.poly([[x0, ye, z1], [x1, ye, z1], [xm, yr, z1 - k]], R); P.poly([[x1, ye, z0], [x0, ye, z0], [xm, yr, z0 + k]], R);
  }
  P.wall(x0, z1, x1, z1, ye - 0.022, ye, fa); P.wall(x1, z1, x1, z0, ye - 0.022, ye, fa); P.wall(x0, z0, x0, z1, ye - 0.022, ye, fa);
}
// a mansard: steep lower slopes with dormers, a low hip above
export function mansard(P, b, o) {
  const { x0, x1, z0, z1, y } = b, i = o.inset ?? 0.1, h = o.lower ?? 0.26, R = roofPaint(o.roof), yt = y + h;
  P.poly([[x0, y, z1], [x1, y, z1], [x1 - i, yt, z1 - i], [x0 + i, yt, z1 - i]], R); P.poly([[x1, y, z0], [x0, y, z0], [x0 + i, yt, z0 + i], [x1 - i, yt, z0 + i]], R);
  P.poly([[x1, y, z1], [x1, y, z0], [x1 - i, yt, z0 + i], [x1 - i, yt, z1 - i]], R); P.poly([[x0, y, z0], [x0, y, z1], [x0 + i, yt, z1 - i], [x0 + i, yt, z0 + i]], R);
  hip(P, { x0: x0 + i, x1: x1 - i, z0: z0 + i, z1: z1 - i, y: yt }, { rise: o.upper ?? 0.1, over: 0.01, roof: o.roofUpper ?? o.roof, fascia: o.trim });
  for (const dx of o.dormers ?? []) dormer(P, dx, z1 - i * 0.45, y + h * 0.25, o);
}
export function dormer(P, x, z, y, o) {   // a small gabled dormer on a front slope, its window in o.dormerWin
  const w = o.dw ?? 0.13, h = o.dh ?? 0.13, d = 0.14;
  P.wall(x - w / 2, z, x + w / 2, z, y, y + h, o.dormerWin);
  P.wall(x + w / 2, z, x + w / 2, z - d, y, y + h, o.trim); P.wall(x - w / 2, z - d, x - w / 2, z, y, y + h, o.trim);
  gable(P, { x0: x - w / 2, x1: x + w / 2, z0: z - d, z1: z, y: y + h }, { rise: 0.07, over: 0.015, along: 'z', roof: o.roof, end: o.trim, fascia: o.trim, ridge: o.ridgeColor });
}
export function chimney(P, x, z, y0, y1, css = '#7b4a3a') { P.box(x - 0.035, y0, z - 0.035, x + 0.035, y1, z + 0.035, css); P.box(x - 0.045, y1, z - 0.045, x + 0.045, y1 + 0.02, z + 0.045, '#5d5752'); }

// ---------- trim on walls ----------
export const ring = (P, b, y, t, e, css) => P.box(b.x0 - e, y, b.z0 - e, b.x1 + e, y + t, b.z1 + e, css, { faces: 'px nx pz nz py' });
// a balcony slab with a railing panel along the front (x0..x1 at z), depth d, rail painted by `rail`
export function balcony(P, x0, x1, z, y, d, rail, slab = '#d9d2c4') {
  P.box(x0, y - 0.018, z, x1, y, z + d, slab, { faces: 'px nx pz ny py', ao: 0.9 });
  P.wall(x0, z + d, x1, z + d, y, y + 0.075, rail); P.wall(x1, z + d, x1, z, y, y + 0.075, rail); P.wall(x0, z, x0, z + d, y, y + 0.075, rail);
}
export const railing = (bar, bg) => (g, wu, hu) => {   // bars on a darker ground, top rail
  g.fillStyle = bg; g.fillRect(-0.05, -0.05, wu + 0.1, hu + 0.1); g.fillStyle = bar;
  for (let x = 0; x < wu; x += 0.022) g.fillRect(x, 0, 0.007, hu); g.fillRect(-0.05, 0, wu + 0.1, hu * 0.16); g.fillRect(-0.05, hu * 0.86, wu + 0.1, hu * 0.14);
};
export const boards = (wood, flowers) => (g, wu, hu) => {   // chalet balcony: vertical boards, flower boxes in front
  material(g, { mat: 'timber', tile: 0.4, tint: wood, rot: 90 }, -0.05, -0.05, wu + 0.1, hu + 0.1);
  g.fillStyle = 'rgba(0,0,0,0.35)'; for (let x = 0.01; x < wu; x += 0.028) g.fillRect(x, hu * 0.3, 0.008, hu * 0.35);
  if (flowers) for (let x = 0.02; x < wu; x += 0.012) { g.fillStyle = rand() < 0.25 ? '#3c6b2c' : flowers; g.beginPath(); g.arc(x, 0.008 + rand() * 0.016, 0.008, 0, 7); g.fill(); }
};
export function columns(P, x0, x1, n, z, y0, y1, r, css = '#ece5d6') {
  for (let k = 0; k < n; k++) {
    const x = x0 + ((x1 - x0) * k) / (n - 1);
    P.cyl(x, z, r, r * 0.86, y0 + 0.03, y1 - 0.03, 10, css);
    P.box(x - r * 1.3, y0, z - r * 1.3, x + r * 1.3, y0 + 0.03, z + r * 1.3, css); P.box(x - r * 1.35, y1 - 0.03, z - r * 1.35, x + r * 1.35, y1, z + r * 1.35, css);
  }
}
// a fabric awning over a window or door: sloping cloth and a valance in the owner's stripes
export function awning(P, x, z, y, w, color, d = 0.09) {
  const s = stripes(color, Math.max(4, Math.round(w / 0.03)));
  P.poly([[x - w / 2, y - 0.05, z + d], [x + w / 2, y - 0.05, z + d], [x + w / 2, y, z], [x - w / 2, y, z]], s, { ao: 0.95 });
  P.wall(x - w / 2, z + d, x + w / 2, z + d, y - 0.075, y - 0.05, s);
  P.poly([[x + w / 2, y - 0.05, z + d], [x + w / 2, y - 0.05, z], [x + w / 2, y, z]], color); P.poly([[x - w / 2, y - 0.05, z], [x - w / 2, y - 0.05, z + d], [x - w / 2, y, z]], color);
}
export function flag(P, x, z, y0, h, color, crest = '#f4efe4') {   // a pole and a hanging flag, two-sided
  P.cyl(x, z, 0.007, 0.006, y0, y0 + h, 6, '#d8d8d2'); P.box(x - 0.012, y0 + h, z - 0.012, x + 0.012, y0 + h + 0.02, z + 0.012, '#c9a54a');
  const f = (g, wu, hu) => { g.fillStyle = color; g.fillRect(-0.05, -0.05, wu + 0.1, hu + 0.1); g.fillStyle = crest; g.fillRect(wu * 0.12, hu * 0.42, wu * 0.76, hu * 0.06); g.fillStyle = 'rgba(0,0,0,0.18)'; g.fillRect(wu * 0.7, 0, wu * 0.3, hu); };
  const t = y0 + h - 0.01, L = 0.13, H = 0.08;
  P.poly([[x + 0.008, t - H, z], [x + L, t - H - 0.01, z + 0.01], [x + L, t - 0.01, z + 0.01], [x + 0.008, t, z]], f);
  P.poly([[x + L, t - H - 0.01, z + 0.008], [x + 0.008, t - H, z - 0.002], [x + 0.008, t, z - 0.002], [x + L, t - 0.01, z + 0.008]], f);
}
// a sign board standing on a roof or ledge, facing +z, letters lit by `lit` (0..0.8)
export function roofSign(P, x, z, y, w, text, color, lit, h = 0.22) {
  P.wall(x - w / 2, z, x + w / 2, z, y + 0.03, y + 0.03 + h, signPaint(text, color, lit));
  P.box(x - w / 2, y + 0.03, z - 0.012, x + w / 2, y + 0.03 + h, z - 0.002, '#26272b', { faces: 'px nx py nz' });
  for (const k of [-0.35, 0.35]) P.box(x + k * w - 0.008, y, z - 0.03, x + k * w + 0.008, y + 0.04, z - 0.012, '#3a3a3a');
}

// ---------- building site ----------
// a scaffold round a box: standards, ledgers on every lift, planked decks, diagonal bracing on the long sides
export function scaffold(P, b, h, lift = 0.2) {
  const e = 0.045, w = 0.07, x0 = b.x0 - e - w, x1 = b.x1 + e + w, z0 = b.z0 - e - w, z1 = b.z1 + e + w, POLE = '#9aa0a3', PLANK = '#a07a4a', t = 0.006;
  const pole = (x, z) => P.box(x - t, 0, z - t, x + t, h, z + t, POLE, { faces: 'px nx pz nz' });
  const along = (a, c, step) => { const n = Math.max(1, Math.round((c - a) / step)); return Array.from({ length: n + 1 }, (_, i) => a + ((c - a) * i) / n); };
  for (const x of along(x0, x1, 0.26)) { pole(x, z1); pole(x, z1 - w); pole(x, z0); }
  for (const z of along(z0, z1, 0.26)) { pole(x0, z); pole(x1, z); pole(x0 + w, z); pole(x1 - w, z); }
  for (let y = lift; y <= h + 0.001; y += lift) {
    P.box(x0, y - t, z1 - t, x1, y + t, z1 + t, POLE, { faces: 'pz py' }); P.box(x0, y + 0.04, z1 - t, x1, y + 0.04 + 2 * t, z1 + t, POLE, { faces: 'pz py' });
    P.box(x1 - t, y - t, z0, x1 + t, y + t, z1, POLE, { faces: 'px py' }); P.box(x0 - t, y - t, z0, x0 + t, y + t, z1, POLE, { faces: 'nx py' });
    P.box(x0, y - 0.008, z1 - w, x1, y, z1, PLANK, { faces: 'py pz' }); P.box(x1 - w, y - 0.008, z0, x1, y, z1, PLANK, { faces: 'py px' }); P.box(x0, y - 0.008, z0, x0 + w, y, z1, PLANK, { faces: 'py nx' });
    P.box(x0, y + 0.012, z1 - 0.002, x1, y + 0.03, z1 + 0.002, '#e8e2d0', { faces: 'pz' });   // toe board
  }
  for (let y = 0; y + lift <= h + 0.001; y += lift * 2) {   // diagonal braces on the front
    for (let x = x0; x + 0.52 <= x1 + 0.001; x += 0.52) P.poly([[x, y, z1 + 0.007], [x + 0.012, y, z1 + 0.007], [x + 0.52, y + lift * 2, z1 + 0.007], [x + 0.508, y + lift * 2, z1 + 0.007]], POLE);
  }
}
// a tower crane: lattice mast, slewing jib with trolley and hook, counter-jib with ballast, cab. Returns the jib group.
export function towerCrane(P, J, H, jib, counter, paint = '#e9e6dc') {
  const m = 0.06, L = (x0, y0, z0, x1, y1, z1, css) => P.box(x0, y0, z0, x1, y1, z1, css, { faces: 'px nx pz nz' });
  for (const [x, z] of [[-m, -m], [m, -m], [m, m], [-m, m]]) L(x - 0.008, 0, z - 0.008, x + 0.008, H, z + 0.008, paint);
  for (let y = 0; y < H; y += 0.12) {   // lacing: a diagonal on every side, horizontals each panel
    P.poly([[-m, y, m + 0.004], [-m + 0.012, y, m + 0.004], [m, y + 0.12, m + 0.004], [m - 0.012, y + 0.12, m + 0.004]], paint);
    P.poly([[m + 0.004, y, m], [m + 0.004, y, m - 0.012], [m + 0.004, y + 0.12, -m], [m + 0.004, y + 0.12, -m + 0.012]], paint);
    P.poly([[-m - 0.004, y, -m], [-m - 0.004, y, -m + 0.012], [-m - 0.004, y + 0.12, m], [-m - 0.004, y + 0.12, m - 0.012]], paint);
    L(-m, y, m - 0.004, m, y + 0.01, m + 0.004, paint); L(m - 0.004, y, -m, m + 0.004, y + 0.01, m, paint);
  }
  P.box(-0.3, 0, -0.3, 0.3, 0.06, 0.3, '#8d8a84');   // foundation block
  // the slewing part, around the mast top (J is a Part whose origin sits on the mast axis at height H)
  J.box(-0.08, 0, -0.07, 0.06, 0.12, 0.07, '#d8d4c8'); J.box(0.06, 0.02, -0.05, 0.16, 0.11, 0.05, '#3b4048');   // turntable, cab
  J.poly([[0.16, 0.025, 0.051], [0.16, 0.025, -0.051], [0.16, 0.105, -0.051], [0.16, 0.105, 0.051]], '#8fb7c9');   // cab window
  for (const z of [-0.04, 0.04]) J.box(0, 0.12, z - 0.007, jib, 0.134, z + 0.007, paint, { faces: 'py pz nz' });
  J.box(0, 0.24, -0.007, jib * 0.92, 0.252, 0.007, paint, { faces: 'py pz nz' });
  for (let x = 0; x < jib - 0.1; x += 0.1) { J.poly([[x, 0.13, 0.044], [x + 0.012, 0.13, 0.044], [x + 0.1, 0.245, 0.004], [x + 0.088, 0.245, 0.004]], paint); J.poly([[x + 0.1, 0.13, -0.044], [x + 0.088, 0.13, -0.044], [x, 0.245, -0.004], [x + 0.012, 0.245, -0.004]], paint); }
  J.box(-0.012, 0.12, -0.012, 0.012, 0.5, 0.012, paint, { faces: 'px nx pz nz' });   // tower head with its pendants
  J.poly([[0.0, 0.5, 0.004], [jib * 0.7 - 0.012, 0.25, 0.004], [jib * 0.7, 0.25, 0.004], [0.012, 0.5, 0.004]], '#7c7c7c');
  J.poly([[0.0, 0.5, 0.004], [-0.012, 0.5, 0.004], [-counter, 0.15, 0.004], [-counter + 0.012, 0.15, 0.004]], '#7c7c7c');
  J.box(-counter, 0.11, -0.05, 0, 0.14, 0.05, paint, { faces: 'py pz nz nx' });
  J.box(-counter, 0.0, -0.06, -counter + 0.2, 0.11, 0.06, '#a7a39a');   // ballast
  const tx = jib * 0.62; J.box(tx - 0.03, 0.105, -0.035, tx + 0.03, 0.12, 0.035, '#4a4f55');
  J.box(tx - 0.002, -1.35, -0.002, tx + 0.002, 0.105, 0.002, '#2c2c2c', { faces: 'pz px' });   // hoist rope, block and a load of planks
  J.box(tx - 0.025, -1.42, -0.02, tx + 0.025, -1.35, 0.02, '#c9a227'); J.box(tx - 0.14, -1.5, -0.05, tx + 0.14, -1.45, 0.05, '#a07a4a');
}

export * from './props-garden.mjs';
export { flat, stripes };
