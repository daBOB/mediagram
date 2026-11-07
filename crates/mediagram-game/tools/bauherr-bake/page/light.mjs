// Light for the realistic looks: a low sun, a sky dome, ground bounce and an optional horizon rim, evaluated on
// the CPU and baked into vertex colours and the ground print. The box then draws the town unlit (MeshBasic):
// sampling a shadow map or lighting per pixel is what costs on this Mali.
import * as THREE from 'three';

export function makeLight(p) {
  const L = new THREE.Vector3(...p.sunDir).normalize();
  const c = (hex, k) => new THREE.Color(hex).multiplyScalar(k);   // THREE.Color from hex is linear
  const sun = c(p.sun, p.sunI), sky = c(p.sky, p.skyI), bounce = c(p.bounce, p.bounceI);
  const rim = p.rim && { d: new THREE.Vector3(...p.rim.dir).normalize(), c: c(p.rim.color, p.rim.i) };
  const out = [0, 0, 0];
  // linear light on a surface with world normal (nx, ny, nz); sunlit 0..1, ao 0..1 (sky and bounce only)
  function at(nx, ny, nz, sunlit = 1, ao = 1) {
    const ndl = Math.max(0, nx * L.x + ny * L.y + nz * L.z) * sunlit, up = 0.5 + 0.5 * ny;
    const s = (0.45 + 0.55 * up) * ao, b = (1 - up) * ao;
    out[0] = sun.r * ndl + sky.r * s + bounce.r * b;
    out[1] = sun.g * ndl + sky.g * s + bounce.g * b;
    out[2] = sun.b * ndl + sky.b * s + bounce.b * b;
    if (rim) { const k = Math.max(0, nx * rim.d.x + ny * rim.d.y + nz * rim.d.z) * ao; out[0] += rim.c.r * k; out[1] += rim.c.g * k; out[2] += rim.c.b * k; }
    for (let i = 0; i < 3; i++) out[i] = knee(out[i]);
    return out;
  }
  // soft highlight roll-off: walls square to a low sun stay golden instead of clipping to flat orange
  const knee = (v) => (v <= 0.7 ? v : 0.7 + (v - 0.7) / (1 + (v - 0.7) / 0.5));
  // contact darkening near the ground: walls and trunks get darker towards their foot
  const groundAO = (y) => 1 - p.groundAO * Math.exp(-Math.max(0, y) / 0.16);
  const up = at(0, 1, 0).slice(), shade = at(0, 1, 0, 0).slice();
  // what a shadow does to the lit ground, per channel (the ground print multiplies by this)
  const shadowRatio = shade.map((v, i) => v / up[i]);
  // ray from a point towards the sun hits the ground plane at p - L * (y / L.y): the shadow offset per unit height
  const drop = { x: -L.x / L.y, z: -L.z / L.y };
  return { L, at, groundAO, up, shade, shadowRatio, drop, sun: [sun.r, sun.g, sun.b], sunI: p.sunI, p };
}

// linear multiplier → CSS colour for canvas 'multiply' (values must be ≤ 1; light above 1 stays in the vertex colours)
export const cssOf = (lin) => `rgb(${lin.map((v) => Math.round(255 * Math.min(1, Math.max(0, v)) ** (1 / 2.2))).join(',')})`;
