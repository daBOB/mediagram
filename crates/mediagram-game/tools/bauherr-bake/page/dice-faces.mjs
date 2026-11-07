// The two dice, painted as 3 x 2 cells of 256 px (box faces +x -x +y -y +z -z): an ivory number die with drilled pips
// and an oak permit die with burnt-in signs. The TV maps the cells onto its dice (client/src/tv/bauherr/bauherr-dice.ts).
import { photo } from './paint.mjs';
import { canvas } from './util.mjs';

const NUMBER_FACES = [1, 6, 2, 5, 3, 4], PERMIT_FACES = ['ok', 'no', 'ok', 'free', 'ok', 'x2'];
// permit die faces: tick (granted), cross (refused), a gift (free), ×2 (pay double); ink = { ok, no, free, x2, bg }
export function permitFace(g, face, ink, s = 128) {
  g.save(); g.translate(s / 2, s / 2); g.lineCap = 'round'; g.lineJoin = 'round';
  if (face === 'ok') { g.strokeStyle = ink.ok; g.lineWidth = s * 0.13; g.beginPath(); g.moveTo(-s * 0.26, s * 0.02); g.lineTo(-s * 0.07, s * 0.21); g.lineTo(s * 0.28, -s * 0.2); g.stroke(); }
  else if (face === 'no') { g.strokeStyle = ink.no; g.lineWidth = s * 0.13; g.beginPath(); g.moveTo(-s * 0.22, -s * 0.22); g.lineTo(s * 0.22, s * 0.22); g.moveTo(s * 0.22, -s * 0.22); g.lineTo(-s * 0.22, s * 0.22); g.stroke(); }
  else if (face === 'free') {
    g.fillStyle = ink.free; g.fillRect(-s * 0.26, -s * 0.06, s * 0.52, s * 0.32); g.fillRect(-s * 0.3, -s * 0.17, s * 0.6, s * 0.12);
    g.fillStyle = ink.bg; g.fillRect(-s * 0.035, -s * 0.17, s * 0.07, s * 0.43);
    g.strokeStyle = ink.free; g.lineWidth = s * 0.05; g.beginPath(); g.ellipse(-s * 0.1, -s * 0.25, s * 0.1, s * 0.055, 0.4, 0, 7); g.moveTo(s * 0.2, -s * 0.25); g.ellipse(s * 0.1, -s * 0.25, s * 0.1, s * 0.055, -0.4, 0, 7); g.stroke();
  } else { g.fillStyle = ink.x2; g.font = `800 ${s * 0.46}px "Bricolage Grotesque",Roboto,sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('×2', 0, s * 0.03); }
  g.restore();
}
// number die pips on a 128 px face
export const PIPS = { 1: [[64, 64]], 2: [[36, 36], [92, 92]], 3: [[34, 34], [64, 64], [94, 94]], 4: [[36, 36], [92, 36], [36, 92], [92, 92]], 5: [[34, 34], [94, 34], [64, 64], [34, 94], [94, 94]], 6: [[36, 30], [92, 30], [36, 64], [92, 64], [36, 98], [92, 98]] };

const faces = (draw) => { const c = canvas(768, 512), g = c.getContext('2d'); for (let k = 0; k < 6; k++) { g.save(); g.translate((k % 3) * 256, (1 - Math.floor(k / 3)) * 256); g.scale(2, 2); draw(g, k); g.restore(); } return c; };
export const ivoryDie = () => faces((g, k) => {
  g.fillStyle = '#eee6d3'; g.fillRect(0, 0, 128, 128); photo(g, 'plastered_wall_04', 0, 0, 128, 128, 200, { alpha: 0.5 });
  for (const [x, y] of PIPS[NUMBER_FACES[k]]) { const r = g.createRadialGradient(x - 2, y - 2, 1, x, y, 11); r.addColorStop(0, '#0d0d0f'); r.addColorStop(0.75, '#26241f'); r.addColorStop(0.92, 'rgba(120,110,95,0.9)'); r.addColorStop(1, 'rgba(238,230,211,0)'); g.fillStyle = r; g.beginPath(); g.arc(x, y, 12, 0, 7); g.fill(); }
});
export const oakDie = () => faces((g, k) => {
  photo(g, 'weathered_plank_siding', 0, 0, 128, 128, 260, { tint: '#f2d9b0', rot: 90 }); g.fillStyle = 'rgba(214,170,110,0.45)'; g.fillRect(0, 0, 128, 128);
  permitFace(g, PERMIT_FACES[k], { ok: '#1f4a2a', no: '#7a1f17', free: '#4a2a12', x2: '#3a2414', bg: '#d8b98a' });
});
