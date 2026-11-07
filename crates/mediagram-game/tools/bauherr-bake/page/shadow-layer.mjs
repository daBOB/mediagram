// Baked sun shadows: world-space silhouettes projected along the sun, softened by down- and up-scaling (blur filters
// stall the box CPU, and the ground print is made offline anyway), with darker contact shadows under footprints.
import { canvas } from './util.mjs';
import { cssOf } from './light.mjs';

// convex hull of 2D points (monotone chain)
export function hull(pts) {
  const p = [...pts].sort((a, b) => a[0] - b[0] || a[1] - b[1]), cr = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lo = [], up = [];
  for (const q of p) { while (lo.length > 1 && cr(lo[lo.length - 2], lo[lo.length - 1], q) <= 0) lo.pop(); lo.push(q); }
  for (const q of p.reverse()) { while (up.length > 1 && cr(up[up.length - 2], up[up.length - 1], q) <= 0) up.pop(); up.push(q); }
  return lo.slice(0, -1).concat(up.slice(0, -1));
}
// world points [x, y, z] → their sun shadow on the ground (with the base points, so the shadow joins the foot)
export const project = (pts, drop) => hull(pts.map(([x, y, z]) => [x + drop.x * y, z + drop.z * y]).concat(pts.map(([x, , z]) => [x, z])));

// paint the shadows of `sh` ({ polys, trees, lines, ao }) into a mask at 1/q resolution, softened by a 1/4q copy;
// draws onto g (whose transform maps world → its px) as multiply with `ratio`. Also used for the dynamic shadow quads.
export function shadowLayer(w, h, toPx, sh, light, look, q = 4) {
  const W = Math.ceil(w / q), H = Math.ceil(h / q), m = canvas(W, H), g = m.getContext('2d'), k = 1 / q;
  const P = ([x, z]) => { const [a, b] = toPx(x, z); return [a * k, b * k]; };
  const fill = (poly) => { g.beginPath(); poly.forEach((v, i) => { const [a, b] = P(v); i ? g.lineTo(a, b) : g.moveTo(a, b); }); g.closePath(); g.fill(); };
  const drop = light.drop, dl = Math.hypot(drop.x, drop.z), ang = Math.atan2(drop.z, drop.x);
  g.fillStyle = '#000';
  if (look.sunShadows) {
    for (const poly of sh.polys) fill(project(poly, drop));
    for (const t of sh.trees) {   // crown: an ellipse stretched along the sun, trunk: a thin band
      const [a, b] = P([t.x + drop.x * t.h, t.z + drop.z * t.h]), [r] = [t.r * (toPx(1, 0)[0] - toPx(0, 0)[0]) * k];
      g.save(); g.translate(a, b); g.rotate(ang); g.beginPath(); g.ellipse(0, 0, r * (1 + dl * 0.35), r * 0.9, 0, 0, 7); g.fill(); g.restore();
      fill([[t.x - 0.02, t.z], [t.x + 0.02, t.z], [t.x + drop.x * t.h * 0.6 + 0.02, t.z + drop.z * t.h * 0.6], [t.x + drop.x * t.h * 0.6 - 0.02, t.z + drop.z * t.h * 0.6]]);
    }
    for (const l of sh.lines) fill([[l.x - l.w, l.z], [l.x + l.w, l.z], [l.x + drop.x * l.h + l.w, l.z + drop.z * l.h], [l.x + drop.x * l.h - l.w, l.z + drop.z * l.h]]);
  }
  // soften: a quarter-size copy scaled back up under the sharp mask makes a penumbra
  const s = canvas(Math.ceil(W / 4), Math.ceil(H / 4)); s.getContext('2d').drawImage(m, 0, 0, s.width, s.height);
  const soft = canvas(W, H), sg = soft.getContext('2d'); sg.imageSmoothingQuality = 'high'; sg.globalAlpha = 0.75; sg.drawImage(s, 0, 0, W, H); sg.globalAlpha = 0.85; sg.drawImage(m, 0, 0);
  // contact shadows: the footprints, darker and tighter
  const ao = canvas(Math.ceil(W / 2), Math.ceil(H / 2)), ag = ao.getContext('2d'); ag.fillStyle = '#000';
  for (const poly of sh.ao) { ag.beginPath(); poly.forEach((v, i) => { const [a, b] = P(v); i ? ag.lineTo(a / 2, b / 2) : ag.moveTo(a / 2, b / 2); }); ag.closePath(); ag.fill(); }
  for (const t of sh.trees) { const [a, b] = P([t.x, t.z]); ag.beginPath(); ag.arc(a / 2, b / 2, t.r * 0.55 * (toPx(1, 0)[0] - toPx(0, 0)[0]) * k / 2, 0, 7); ag.fill(); }
  const aoS = canvas(Math.ceil(W / 8), Math.ceil(H / 8)); aoS.getContext('2d').drawImage(ao, 0, 0, aoS.width, aoS.height);
  // colourise: shadow = sky light only (light.shadowRatio); contact = a neutral darkening
  const out = canvas(W, H), og = out.getContext('2d'); og.imageSmoothingQuality = 'high';
  og.drawImage(soft, 0, 0); og.globalCompositeOperation = 'source-in'; og.fillStyle = cssOf(light.shadowRatio); og.fillRect(0, 0, W, H);
  const c2 = canvas(W, H), cg = c2.getContext('2d'); cg.drawImage(aoS, 0, 0, W, H); cg.globalCompositeOperation = 'source-in'; cg.fillStyle = `rgba(0,0,0,${look.contact ?? 0.5})`; cg.fillRect(0, 0, W, H);
  og.globalCompositeOperation = 'source-over'; og.globalAlpha = 1; og.drawImage(c2, 0, 0);
  return out;   // draw with 'multiply' at full size
}

