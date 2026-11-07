// Big props built into the town part in world units: the city blocks behind the Stadtpalais and the bridge. (Mountains,
// hill and coast rocks are terrain: terrain-field.mjs.)
import * as V from './paint.mjs';
import { walls } from './hotel-kit.mjs';
import { BRIDGE } from './scenery-map.mjs';
import { ROAD_WIDTH } from '../../../client/src/tv/bauherr/bauherr-layout.ts';

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
