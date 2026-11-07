// Landforms and big props, built into the town part in world units: mountains with snow behind the Alpenrose, a hill
// behind the Sternwarte, rocks and a cliff on the Leuchtturm's coast, city blocks behind the Stadtpalais, the bridge.
import * as THREE from 'three';
import * as V from './paint.mjs';
import { rand } from './util.mjs';
import { walls } from './hotel-kit.mjs';
import { BRIDGE } from './scenery-map.mjs';
import { ROAD_WIDTH } from '../../../client/src/tv/bauherr/bauherr-layout.ts';

const { photo, tint, shade } = V;
// rock with snow above `snow` (0 top .. 1 base), or grass; painted once and wrapped round the peak
const rockPaint = (snow, css = '#8c8276') => (g, wu, hu) => {
  photo(g, 'stone_wall', -0.1, -0.1, wu + 0.2, hu + 0.2, 1.4, { tint: css });
  shade(g, 0, hu * 0.55, wu, hu * 0.45, 'rgba(60,70,40,0)', 'rgba(70,80,40,0.45)');
  if (snow > 0) {
    g.fillStyle = '#f4f6fa'; g.beginPath(); g.moveTo(0, 0);
    for (let x = 0; x <= wu; x += wu / 40) g.lineTo(x, hu * (snow + 0.1 * Math.sin(x * 7.1) * Math.cos(x * 2.3) + (rand() - 0.5) * 0.04));
    g.lineTo(wu, 0); g.closePath(); g.fill();
    tint(g, 0, 0, wu, hu * (snow + 0.1), 'rgba(205,215,235,0.35)');
  }
};
const grassPaint = (css) => (g, wu, hu) => { photo(g, V.over('sparse_grass', css, 'soft-light', 2), -0.1, -0.1, wu + 0.2, hu + 0.2, 1.4); };

/** An irregular peak: rings of jittered points up to an apex, faceted. o: { snow, jag, rock, grass, rings, seg } */
export function peak(P, cx, cz, r, h, o = {}) {
  const seg = o.seg ?? 11, rings = o.rings ?? 4, jag = o.jag ?? 0.22, ppu = o.ppu ?? 50;
  const paint = o.grass ? grassPaint(o.grass) : rockPaint(o.snow ?? 0, o.rock);
  const face = P.atlas.face(2 * Math.PI * r, h, paint, ppu), pts = [];
  for (let j = 0; j <= rings; j++) {
    const k = j / rings, rr = r * Math.pow(1 - k, o.round ?? 0.9) * (j === rings ? 0 : 1);
    pts.push(Array.from({ length: seg }, (_, i) => {
      const a = (i / seg) * Math.PI * 2, f = 1 + (rand() - 0.5) * jag * (j === 0 ? 0.5 : 1.4);
      return new THREE.Vector3(cx + Math.sin(a) * rr * f, h * (k + (j && j < rings ? (rand() - 0.5) * jag * 0.35 : 0)) * (o.flat ?? 1), cz + Math.cos(a) * rr * f);
    }));
  }
  const mid = new THREE.Vector3(cx, h * 0.4, cz);
  const tri = (a, b, c) => {   // one facet, wound to face away from the peak's axis
    let n = new THREE.Vector3().subVectors(b[0], a[0]).cross(new THREE.Vector3().subVectors(c[0], a[0])).normalize();
    const ctr = new THREE.Vector3().add(a[0]).add(b[0]).add(c[0]).divideScalar(3);
    if (n.dot(ctr.sub(mid)) < 0) { [b, c] = [c, b]; n = n.negate(); }
    const base = P.verts.length;
    for (const [q, i, j] of [a, b, c]) P.verts.push({ q, n, r: face, s: i / seg, t: 1 - j / rings, ao: 1, sun: 1, tint: 0 });
    P.idx.push(base, base + 1, base + 2);
  };
  for (let j = 0; j < rings; j++) {
    for (let i = 0; i < seg; i++) {
      const p = (jj, ii) => [pts[jj][ii % seg], ii, jj];
      tri(p(j, i), p(j, i + 1), p(j + 1, i + 1)); tri(p(j, i), p(j + 1, i + 1), p(j + 1, i));
    }
  }
  // the base ring's outline, for the shadow: apex and ring points
  return [...pts[0].map((q) => [q.x, 0, q.z]), [cx, h * (o.shadowK ?? 1), cz]];
}

/** Mountains behind the Alpenrose: a range of snow peaks over foothills. */
export function mountains(P, sh) {
  const list = [[15, -17, 5.2, 10.5], [10.2, -19.5, 4.6, 8.2], [19.5, -20, 6, 12.5], [21.5, -14.2, 4, 6], [6, -22, 4.2, 7], [13, -13.8, 3.6, 3.4, true], [17.5, -13, 3.2, 2.6, true]];
  for (const [x, z, r, h, foot] of list) sh.polys.push(peak(P, x, z, r, h, foot ? { grass: '#7d9156', jag: 0.1, rings: 5, round: 0.6, seg: 14, ppu: 60 } : { snow: 0.42, jag: 0.28, rings: 5, shadowK: 0.4 }));
}
/** The hill behind the Sternwarte. */
export function hill(P, sh) { sh.polys.push(peak(P, 11.2, -3.0, 2.5, 1.5, { grass: '#7d9a52', jag: 0.06, rings: 4, round: 0.55, seg: 14, ppu: 70 })); }
/** Boulders along the coast beside the Leuchtturm, rounded and grey, half in the surf. */
export function coast(P, sh) {
  const rock = (x, z, r, h) => sh.polys.push(peak(P, x, z, r, h, { rock: '#bdb3a4', jag: 0.18, rings: 4, seg: 10, round: 0.45, ppu: 90 }));
  for (let i = 0; i < 12; i++) rock(-15.5 + i * 0.95 + rand() * 0.4, 6.35 + Math.sin(i * 1.7) * 0.25, 0.3 + rand() * 0.35, 0.2 + rand() * 0.3);
  for (const [x, z, r, h] of [[-6.4, 8.6, 0.5, 0.5], [-5.5, 10.4, 0.4, 0.4], [-8, 7.6, 0.4, 0.35]]) rock(x, z, r, h);
}
/** City blocks behind the Stadtpalais: plain plaster and brick fronts with lit windows. */
export function cityBlocks(P, sh, look) {
  const specs = [
    { mat: 'plaster', tint: '#d9cdb4' }, { mat: 'brick', tile: 0.5 }, { mat: 'plaster', tint: '#e2c9a0' }, { mat: 'plaster', tint: '#cfd4d6' }, { mat: 'brick', tile: 0.5 }, { mat: 'plaster', tint: '#e6dcc8' },
  ];
  const blocks = [[0.6, -16.4, 2.8, 2.4, 2.6], [3.8, -17.4, 2.6, 2.6, 3.2], [7.0, -16.2, 2.8, 2.4, 2.4], [2.0, -20.4, 3.0, 2.6, 3.8], [5.8, -20.8, 2.8, 2.6, 3.0], [9.0, -19.6, 2.6, 2.8, 3.4]];
  blocks.forEach(([x, z, w, d, h], i) => {
    const S = { ...specs[i % specs.length], tile: specs[i % specs.length].tile ?? 1.2, groundH: 0.26, floorH: 0.26, top: 0.08, pitch: 0.2, lit: look.lit * 1.4, glowK: look.glowK, bands: true,
      win: { w: 0.085, h: 0.15, frame: '#efe8da', mull: 'cross' }, groundWin: { w: 0.1, h: 0.17, frame: '#efe8da', mull: 'cross' } };
    const b = { x0: x - w / 2, x1: x + w / 2, z0: z - d / 2, z1: z + d / 2 };
    const sc = h / 1.4, bb = { ...b };
    // the facade is painted for a 1.4-tall block and stretched: windows stay readable at this distance
    walls(P, bb, 0, h, { ...S, floorH: S.floorH * sc, groundH: S.groundH * sc, win: { ...S.win, w: S.win.w * 1.6, h: S.win.h * sc }, groundWin: { ...S.groundWin, w: 0.15, h: S.groundWin.h * sc }, pitch: 0.34 }, { door: true, ppu: 80 });
    P.box(b.x0 - 0.04, h, b.z0 - 0.04, b.x1 + 0.04, h + 0.08, b.z1 + 0.04, V.flat({ mat: 'slate', tile: 0.6, tint: '#777' }), { faces: 'px nx pz nz py' });
    const pts = [[b.x0, 0, b.z0], [b.x1, 0, b.z0], [b.x1, 0, b.z1], [b.x0, 0, b.z1], [b.x0, h, b.z0], [b.x1, h, b.z0], [b.x1, h, b.z1], [b.x0, h, b.z1]];
    sh.polys.push(pts); sh.ao.push(pts.slice(0, 4).map(([px, , pz]) => [px, pz]));
  });
}
// a box standing on any four ground points, faces wound outwards
function obox(P, ground, y0, y1, fill) {
  const cx = ground.reduce((a, q) => a + q[0], 0) / 4, cz = ground.reduce((a, q) => a + q[1], 0) / 4;
  let top = ground.map(([x, z]) => [x, y1, z]);
  if (top.reduce((a, q, i) => a + (q[2] - top[(i + 1) % 4][2]) * (q[0] + top[(i + 1) % 4][0]), 0) < 0) top = top.reverse();
  P.poly(top, fill);
  for (let i = 0; i < 4; i++) {
    let [a, b] = [ground[i], ground[(i + 1) % 4]];
    if (-(b[1] - a[1]) * ((a[0] + b[0]) / 2 - cx) + (b[0] - a[0]) * ((a[1] + b[1]) / 2 - cz) < 0) [a, b] = [b, a];
    P.poly([[a[0], y0, a[1]], [b[0], y0, b[1]], [b[0], y1, b[1]], [a[0], y1, a[1]]], fill);
  }
}
/** The bridge where the road crosses the creek: a stone deck with parapets and posts, stepped up from the road. */
export function bridge(P, sh) {
  const { x, z, ang } = BRIDGE, L = 2.7, W = ROAD_WIDTH + 0.5, y = 0.1, c = Math.cos(ang), s = Math.sin(ang);
  const w = (lx, lz) => [x + lx * c - lz * s, z + lx * s + lz * c];
  const box = (lx0, lz0, lx1, lz1, y0, y1, fill) => obox(P, [w(lx0, lz0), w(lx1, lz0), w(lx1, lz1), w(lx0, lz1)], y0, y1, fill);
  box(-L / 2, -W / 2, L / 2, W / 2, 0, y, V.flat({ mat: 'sandstone', tile: 0.5, tint: '#d9ceb8' }));
  const stone = V.flat({ mat: 'sandstone', tile: 0.4, tint: '#e0d6c0' });
  for (const sz of [-1, 1]) {
    box(-L / 2, sz * W / 2 - 0.05, L / 2, sz * W / 2 + 0.05, y, y + 0.2, stone);
    for (const sx of [-1, 1]) box(sx * L / 2 - 0.07, sz * W / 2 - 0.08, sx * L / 2 + 0.07, sz * W / 2 + 0.08, y, y + 0.3, stone);
  }
  sh.polys.push([-L / 2, L / 2].flatMap((a) => [-W / 2, W / 2].flatMap((b) => [[w(a, b)[0], 0, w(a, b)[1]], [w(a, b)[0], 0.3, w(a, b)[1]]])));
}
