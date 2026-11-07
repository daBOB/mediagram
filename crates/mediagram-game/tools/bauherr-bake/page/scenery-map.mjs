// The shapes of the landscape in world units: the lake behind Seeblick, the creek that the road crosses by a bridge,
// the sea and its cliff beside the Leuchtturm, and the zones each hotel's surroundings are painted and planted in.
// Shared by the ground painter and the prop builder, so trees never stand in water.
import { FIELD_COUNT, roadPoint } from '../../../client/src/tv/bauherr/bauherr-layout.ts';

export const LAKE = [[-120, -3], [-30, -3], [-19.5, -4.5], [-16.9, -7.0], [-15.4, -10.2], [-13.6, -12.7], [-9.8, -14.0], [-6.5, -15.8], [-6, -120], [-120, -120]];
export const SEA = [[-120, 6.5], [-14, 6.1], [-9.5, 6.9], [-6.6, 8.6], [-5.0, 11], [-4.6, 15], [-4.4, 120], [-120, 120]];
export const CREEK = [[10, -22], [6, -18], [1.5, -14.5], [0.2, -12], [0.4, -9], [0.45, -6.1], [0.6, -4.8], [0.4, -4.1], [1.3, -3.8]];
export const CREEK_W = 1.15;
export const POND = { x: 1.5, z: -3.8, rx: 1.6, rz: 0.8 };
/** Where the road crosses the creek: the middle of the bridge and the road's direction there. */
export const BRIDGE = (() => {
  let best = { d: 1e9, u: 0 };
  for (let u = 0; u < FIELD_COUNT; u += 0.02) {
    const p = roadPoint(u);
    for (let i = 0; i + 1 < CREEK.length; i++) {
      const [ax, az] = CREEK[i], [bx, bz] = CREEK[i + 1], t = Math.max(0, Math.min(1, ((p.x - ax) * (bx - ax) + (p.z - az) * (bz - az)) / ((bx - ax) ** 2 + (bz - az) ** 2)));
      const d = Math.hypot(p.x - (ax + (bx - ax) * t), p.z - (az + (bz - az) * t));
      if (d < best.d) best = { d, u, p };
    }
  }
  return { x: best.p.x, z: best.p.z, ang: best.p.ang };
})();
/** Rectangles painted as ground of a kind: [x0, z0, x1, z1, kind]. */
export const ZONES = [
  [-10, -21, -0.8, -12.4, 'forest'], [-1.4, -21, 10.5, -12.4, 'city'], [8, -24, 24, -12.4, 'alpine'], [-14.5, -4.6, -6.5, -2.0, 'orchard'], [-1.5, -5.0, 8.6, -2.2, 'park'],
  [-22, -12.4, -12.5, -8.0, 'shore'],
];
export const inPoly = (poly, x, z) => {
  let c = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, zi] = poly[i], [xj, zj] = poly[j];
    if (zi > z !== zj > z && x < ((xj - xi) * (z - zi)) / (zj - zi) + xi) c = !c;
  }
  return c;
};
/** Distance from a point to the creek's middle line. */
export const creekDistance = (x, z) => {
  let best = 1e9;
  for (let i = 0; i + 1 < CREEK.length; i++) {
    const [ax, az] = CREEK[i], [bx, bz] = CREEK[i + 1], t = Math.max(0, Math.min(1, ((x - ax) * (bx - ax) + (z - az) * (bz - az)) / ((bx - ax) ** 2 + (bz - az) ** 2)));
    best = Math.min(best, Math.hypot(x - (ax + (bx - ax) * t), z - (az + (bz - az) * t)));
  }
  return best;
};
export const inWater = (x, z, m = 0) => inPoly(LAKE, x, z) || inPoly(SEA, x, z) || creekDistance(x, z) < CREEK_W / 2 + m || (((x - POND.x) / (POND.rx + m)) ** 2 + ((z - POND.z) / (POND.rz + m)) ** 2 < 1);
