// A building stage casts its shadow once it stands, so every stage gets its own soft shadow tile, packed into one
// shadow atlas per hotel (the darkness the shadow takes away) and drawn as a quad on the ground that fades in as the
// stage rises. The ground print carries the shadows of everything that never changes.
import { Atlas } from './atlas.mjs';
import { project, shadowLayer } from './shadow-layer.mjs';
import { canvas } from './util.mjs';
import { look } from './look.mjs';

const PX = 30, Y = 0.012;   // shadow tile pixels per unit; quads lie just above the ground print

/** H: a built hotel (kit), light: the baked light. Returns the atlas canvas, quad geometry and each stage's [start, count] index range. */
export function hotelShadows(H, light) {
  const SA = new Atlas(1024, 512, PX, "#000"), rects = H.stages.map((s) => {
    const world = (s.cast ?? []).map((q) => H.P.world(q)), foot = (s.foot ?? []).map(([x, z]) => { const w = H.P.world([x, 0, z]); return [w[0], w[2]]; });
    if (!world.length) return null;
    const hull = project(world, light.drop), xs = hull.map((q) => q[0]).concat(foot.map((q) => q[0])), zs = hull.map((q) => q[1]).concat(foot.map((q) => q[1]));
    const x0 = Math.min(...xs) - 0.5, x1 = Math.max(...xs) + 0.5, z0 = Math.min(...zs) - 0.5, z1 = Math.max(...zs) + 0.5;
    const r = SA.face(x1 - x0, z1 - z0, (g, wu, hu) => {
      const w = Math.ceil(wu * PX), h = Math.ceil(hu * PX);
      const layer = shadowLayer(w, h, (x, z) => [(x - x0) * PX, (z - z0) * PX], { polys: [world], trees: [], lines: [], ao: [foot] }, light, look, 1);
      const dark = canvas(w, h), dg = dark.getContext('2d');
      dg.fillStyle = '#fff'; dg.fillRect(0, 0, w, h); dg.drawImage(layer, 0, 0);
      dg.globalCompositeOperation = 'difference'; dg.fillRect(0, 0, w, h);   // 1 − factor: black where the ground stays lit
      g.drawImage(dark, 0, 0, wu, hu);
    });
    return { r, x0, x1, z0, z1 };
  });
  SA.pack();
  const pos = [], uv = [], idx = [], ranges = [];
  for (const q of rects) {
    if (!q) { ranges.push([0, 0]); continue; }
    const b = pos.length / 3, start = idx.length;
    for (const [x, z, s, t] of [[q.x0, q.z1, 0, 1], [q.x1, q.z1, 1, 1], [q.x1, q.z0, 1, 0], [q.x0, q.z0, 0, 0]]) { pos.push(x, Y, z); uv.push(...SA.uv(q.r, s, t)); }
    idx.push(b, b + 1, b + 2, b, b + 2, b + 3);
    ranges.push([start, 6]);
  }
  return { canvas: SA.draw(), pos: Float32Array.from(pos), uv: Float32Array.from(uv), idx: Uint32Array.from(idx), ranges };
}
