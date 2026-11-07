// Things that carry a player's colour: awnings, flags, lit name signs, entrance canopies, property lines and the
// plot sign. They are built into an owner part (tint 1): the colour is a flat white swatch there and the TV tints it
// per vertex with the owner's colour at run time (aTint), so one baked geometry serves every player. What must keep
// its own colour (cream stripes, posts, poles, sign bodies) is built with tint 0.
import { sign as signPaint } from './paint.mjs';

const WHITE = '#ffffff', CREAM = '#efe7d6', IRON = '#2a2c30';

// a fabric awning over a window or door: sloping cloth and a valance in alternating owner and cream stripes
export function awning(O, x, z, y, w, d = 0.11) {
  const n = Math.max(4, Math.round(w / 0.035)), sw = w / n;
  for (let i = 0; i < n; i++) {
    const x0 = x - w / 2 + i * sw, x1 = x0 + sw, fill = i % 2 ? CREAM : WHITE, tint = i % 2 ? 0 : 1;
    O.poly([[x0, y - 0.06, z + d], [x1, y - 0.06, z + d], [x1, y, z], [x0, y, z]], fill, { ao: 0.95, tint });
    O.wall(x0, z + d, x1, z + d, y - 0.09, y - 0.06, fill, { tint });
  }
  for (const s of [-1, 1]) O.poly(s > 0 ? [[x + w / 2, y - 0.06, z + d], [x + w / 2, y - 0.06, z], [x + w / 2, y, z]] : [[x - w / 2, y - 0.06, z], [x - w / 2, y - 0.06, z + d], [x - w / 2, y, z]], WHITE, { tint: 1 });
}
// a pole and a hanging flag in the owner's colour, with a cream band
export function flag(O, x, z, y0, h) {
  O.cyl(x, z, 0.008, 0.006, y0, y0 + h, 6, '#d8d8d2', { tint: 0 });
  O.box(x - 0.013, y0 + h, z - 0.013, x + 0.013, y0 + h + 0.022, z + 0.013, '#c9a54a', { tint: 0 });
  const t = y0 + h - 0.012, L = 0.17, H = 0.1;
  for (const [y1, y2, fill, tint] of [[t - H, t, WHITE, 1], [t - H * 0.62, t - H * 0.46, CREAM, 0]]) {
    const o = fill === CREAM ? 0.003 : 0;
    O.poly([[x + 0.009, y1, z + o], [x + L, y1 - 0.01, z + 0.012 + o], [x + L, y2 - 0.01, z + 0.012 + o], [x + 0.009, y2, z + o]], fill, { tint });
    O.poly([[x + L, y1 - 0.01, z + 0.01 - o], [x + 0.009, y1, z - 0.003 - o], [x + 0.009, y2, z - 0.003 - o], [x + L, y2 - 0.01, z + 0.01 - o]], fill, { tint });
  }
}
// a sign board standing on a roof or ledge, facing +z, white letters on a dark board, lit by `lit` (0..0.8): they show in the owner's colour
export function roofSign(O, x, z, y, w, text, lit, h = 0.26) {
  O.wall(x - w / 2, z, x + w / 2, z, y + 0.03, y + 0.03 + h, signPaint(text, '#ffffff', lit), { tint: 1 });
  O.box(x - w / 2, y + 0.03, z - 0.014, x + w / 2, y + 0.03 + h, z - 0.002, '#26272b', { faces: 'px nx py nz', tint: 0 });
  for (const k of [-0.35, 0.35]) O.box(x + k * w - 0.009, y, z - 0.034, x + k * w + 0.009, y + 0.04, z - 0.012, '#3a3a3a', { tint: 0 });
}
// the entrance canopy on a path field: striped roof on two posts, drawn at the origin facing +z and placed by the TV
export function canopy(O, w = 0.62, d = 0.34, h = 0.46) {
  for (const s of [-1, 1]) for (const k of [-1, 1]) O.cyl(s * (w / 2 - 0.02), k * (d / 2 - 0.02), 0.013, 0.013, 0, h, 6, IRON, { tint: 0 });
  const n = 8, sw = w / n;
  for (let i = 0; i < n; i++) {
    const x0 = -w / 2 + i * sw, x1 = x0 + sw, fill = i % 2 ? CREAM : WHITE, tint = i % 2 ? 0 : 1;
    O.poly([[x0, h + 0.05, d / 2], [x1, h + 0.05, d / 2], [x1, h + 0.05, -d / 2], [x0, h + 0.05, -d / 2]], fill, { tint });
    O.poly([[x0, h + 0.05, d / 2], [x0, h, d / 2 + 0.03], [x1, h, d / 2 + 0.03], [x1, h + 0.05, d / 2]], fill, { tint });
  }
  O.box(-w / 2 - 0.01, h - 0.012, -d / 2 - 0.01, w / 2 + 0.01, h, d / 2 + 0.03, IRON, { faces: 'pz px nx', tint: 0 });
}
// a painted property line round a lot (lot-local rectangle), with a post at every corner
export function propertyLine(O, w, d, t = 0.1) {
  const y = 0.02, hw = w / 2, hd = d / 2;
  for (const [x0, z0, x1, z1] of [[-hw, hd - t, hw, hd], [-hw, -hd, hw, -hd + t], [-hw, -hd, -hw + t, hd], [hw - t, -hd, hw, hd]]) O.poly([[x0, y, z1], [x1, y, z1], [x1, y, z0], [x0, y, z0]], WHITE, { tint: 1 });
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) O.box(sx * hw - 0.03, 0, sz * hd - 0.03, sx * hw + 0.03, 0.2, sz * hd + 0.03, WHITE, { faces: 'px nx pz nz py', tint: 1 });
}
// the sign of a bought lot: a board on two posts, the owner's colour as its header band
export function plotSign(O, x, z, name) {
  const body = (g, wu, hu) => {
    g.fillStyle = '#f4f0e6'; g.fillRect(-0.05, -0.05, wu + 0.1, hu + 0.1);
    const s = wu / 300; g.save(); g.scale(s, s); const H = hu / s; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillStyle = '#2a2f3a'; g.font = `800 ${H * 0.32}px "Bricolage Grotesque", sans-serif`; g.fillText(name, 150, H * 0.34, 280);
    g.font = `700 ${H * 0.2}px "Bricolage Grotesque", sans-serif`; g.fillStyle = '#6a5a48'; g.fillText('Hier baut', 150, H * 0.72, 280); g.restore();
  };
  for (const s of [-0.4, 0.4]) O.box(x + s - 0.016, 0, z - 0.016, x + s + 0.016, 0.62, z + 0.016, '#e9e4d8', { tint: 0 });
  O.wall(x - 0.5, z + 0.02, x + 0.5, z + 0.02, 0.28, 0.52, body, { tint: 0 });
  O.wall(x - 0.5, z + 0.02, x + 0.5, z + 0.02, 0.52, 0.62, WHITE, { tint: 1 });
  O.box(x - 0.5, 0.28, z, x + 0.5, 0.62, z + 0.02, '#e9e4d8', { faces: 'px nx py nz', tint: 0 });
}
