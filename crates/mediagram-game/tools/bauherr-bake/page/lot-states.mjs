// How a lot looks before a hotel stands on it. For sale: a wild meadow with flowers, shrubs and stones, marked out by a
// surveyor's string, and the estate agent's board. Bought: a levelled building site, mown at the edge, with a gravel track
// from the gate, the foundation chalked out and the things a site has lying about. (The owner's fence and sign are in
// owner-props.mjs: they take the owner's colour.) Lot-local units, x across the front, z towards the road.
import * as THREE from 'three';
import { photo, over } from './paint.mjs';
import { rand } from './util.mjs';
import { look } from './look.mjs';
import * as K from './props.mjs';

const SLAB = 0.012, PPU = 90;   // the lot ground is a soft print: 45 px per world unit is enough and spares the atlas
const slab = (P, hw, hd, fill) => P.poly([[-hw, SLAB, hd], [hw, SLAB, hd], [hw, SLAB, -hd], [-hw, SLAB, -hd]], fill, { ao: 0.95, ppu: PPU });
// a tiled photo as a fill, one repeat = `tile` local units
const pat = (g, img, tile) => { const p = g.createPattern(img, 'repeat'); p.setTransform(new DOMMatrix().scale(tile / img.width)); return p; };
const dot = (g, x, y, r) => { g.beginPath(); g.arc(x, y, r, 0, 7); g.fill(); };

// the string round the lot, and a stake with red and white tape at each corner
function survey(g, wu, hu) {
  g.strokeStyle = 'rgba(250,250,240,0.85)'; g.lineWidth = 0.012; g.strokeRect(0.08, 0.08, wu - 0.16, hu - 0.16);
  for (const [a, b] of [[0.08, 0.08], [wu - 0.08, 0.08], [wu - 0.08, hu - 0.08], [0.08, hu - 0.08]]) { g.fillStyle = '#e8e0cc'; dot(g, a, b, 0.03); g.fillStyle = '#d4482f'; dot(g, a, b, 0.015); }
}

const meadowPaint = (g, wu, hu) => {
  photo(g, over('grass_ground', '#8aa857', 'soft-light', 2), -0.05, -0.05, wu + 0.1, hu + 0.1, 0.8);
  for (let i = 0; i < 420; i++) {   // long grass: short strokes, darker and straw-coloured
    const x = rand() * wu, y = rand() * hu, l = 0.03 + rand() * 0.05, a = (rand() - 0.5) * 0.6;
    g.strokeStyle = rand() < 0.6 ? 'rgba(52,86,28,0.4)' : 'rgba(214,200,120,0.35)'; g.lineWidth = 0.006;
    g.beginPath(); g.moveTo(x, y); g.lineTo(x + Math.sin(a) * l, y - Math.cos(a) * l); g.stroke();
  }
  for (const [css, n] of [['#f4efe2', 70], ['#f2d84a', 55], ['#e8789a', 26], ['#8aa6ee', 22]]) { g.fillStyle = css; for (let i = 0; i < n; i++) dot(g, rand() * wu, rand() * hu, 0.007 + rand() * 0.006); }
  for (let i = 0; i < 5; i++) { const x = rand() * wu, y = rand() * hu, r = 0.12 + rand() * 0.2, gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, 'rgba(30,50,15,0.2)'); gr.addColorStop(1, 'rgba(30,50,15,0)'); g.fillStyle = gr; g.fillRect(x - r, y - r, 2 * r, 2 * r); }
  survey(g, wu, hu);
};

// the estate agent's board, facing the road
function forSaleSign(P, name, hd) {
  const x = 0, z = hd - 0.22, board = (g, wu, hu) => {
    g.fillStyle = '#f4f0e6'; g.fillRect(-0.1, -0.1, wu + 0.2, hu + 0.2); g.fillStyle = '#2f5d8a'; g.fillRect(-0.1, -0.1, wu + 0.2, hu * 0.34 + 0.1);
    const s = wu / 300; g.save(); g.scale(s, s); const H = hu / s; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillStyle = '#fff'; g.font = `800 ${H * 0.24}px "Bricolage Grotesque", sans-serif`; g.fillText('Zu verkaufen', 150, H * 0.17, 280);
    g.fillStyle = '#2a2f3a'; g.font = `800 ${H * 0.3}px "Bricolage Grotesque", sans-serif`; g.fillText(name, 150, H * 0.62, 280); g.restore();
  };
  for (const s of [-0.38, 0.38]) P.box(x + s - 0.014, 0, z - 0.014, x + s + 0.014, 0.58, z + 0.014, '#e9e4d8');
  P.box(x - 0.52, 0.28, z + 0.014, x + 0.52, 0.62, z + 0.034, '#e9e4d8', { front: board, faces: 'pz px nx py' });
}

// a stone: a squashed, lumpy ball in grey, lit like everything else
function stone(P, x, z, r) {
  const g = new THREE.IcosahedronGeometry(r, 1), p = g.attributes.position;
  g.deleteAttribute('uv');
  for (let i = 0; i < p.count; i++) { const f = 1 + 0.2 * Math.sin(p.getX(i) * 40 + x) * Math.cos(p.getZ(i) * 33 + p.getY(i) * 21); p.setXYZ(i, p.getX(i) * f * 1.2, p.getY(i) * f * 0.62, p.getZ(i) * f); }
  g.translate(x, r * 0.28, z); g.computeVertexNormals();
  P.mesh(g, rand() < 0.5 ? '#9b968b' : '#aaa497');
}

/** A lot for sale: the meadow, shrubs, stones and the board. `c` is the hotel's build context (its leaf faces). */
export function meadow(c, hw, hd, name) {
  const { P } = c;
  slab(P, hw, hd, meadowPaint);
  for (let i = 0; i < 6; i++) stone(P, (rand() * 2 - 1) * (hw - 0.2), (rand() * 2 - 1) * (hd - 0.3), 0.03 + rand() * 0.04);
  for (const [x, z, s] of [[-hw + 0.28, -hd + 0.3, 0.5], [hw - 0.3, -hd + 0.25, 0.42], [hw - 0.22, hd * 0.35, 0.34], [-hw + 0.25, hd * 0.1, 0.38]]) K.tree(P, x, z, s, c.leaves, 'round');
  forSaleSign(P, name, hd);
}

// the site: mown lawn at the edge, packed earth inside, a gravel track from the gate, the foundation chalked out
const sitePaint = (g, wu, hu) => {
  photo(g, over('sparse_grass', look.grass, 'soft-light', 2), -0.05, -0.05, wu + 0.1, hu + 0.1, 0.8);
  const ex = 0.22, soft = (x, y, w, h, a) => { g.globalAlpha = a; g.fillRect(x, y, w, h); };
  g.save(); g.fillStyle = pat(g, over('park_dirt', '#948a76', 'soft-light', 2), 0.5);
  for (let k = 0; k < 4; k++) soft(ex - 0.06 + k * 0.03, ex - 0.06 + k * 0.03, wu - 2 * ex + 0.12 - k * 0.06, hu - 2 * ex + 0.12 - k * 0.06, 0.28 + k * 0.2);   // the earth fades into the lawn
  g.restore();
  g.save(); g.fillStyle = pat(g, over('gravel_floor', '#b8a98e', 'soft-light', 1), 0.35);
  g.fillRect(wu / 2 - 0.3, hu * 0.45, 0.6, hu * 0.55); g.restore();   // the track from the gate
  g.strokeStyle = 'rgba(60,45,30,0.3)'; g.lineWidth = 0.025;
  for (const s of [-0.14, 0.14]) { g.beginPath(); g.moveTo(wu / 2 + s, hu); g.lineTo(wu / 2 + s * 0.8, hu * 0.42); g.stroke(); }   // tyre tracks
  // the foundation: chalk lines and stakes in the middle of the plot
  const fx0 = wu / 2 - 0.78, fx1 = wu / 2 + 0.78, fz0 = hu / 2 - 0.9, fz1 = hu / 2 + 0.02;
  g.strokeStyle = 'rgba(250,248,238,0.9)'; g.lineWidth = 0.014; g.strokeRect(fx0, fz0, fx1 - fx0, fz1 - fz0);
  for (const [a, b] of [[fx0, fz0], [fx1, fz0], [fx1, fz1], [fx0, fz1]]) { g.fillStyle = '#e8e0cc'; dot(g, a, b, 0.026); g.fillStyle = '#d4482f'; dot(g, a, b, 0.013); }
};

/** A bought lot: the prepared site and what lies on it. */
export function site(c, hw, hd) {
  const { P } = c;
  slab(P, hw, hd, sitePaint);
  const m = hw - 0.14, n = hd - 0.14;
  P.box(-m + 0.05, SLAB, -n + 0.02, -m + 0.62, 0.2, -n + 0.36, '#6f8aa0'); P.box(-m + 0.03, 0.2, -n, -m + 0.64, 0.215, -n + 0.38, '#e6e1d6', { faces: 'px nx py pz nz' });   // the cabin
  for (const [x, z] of [[m - 0.45, -n + 0.2], [m - 0.2, -n + 0.38]]) { P.box(x - 0.11, SLAB, z - 0.07, x + 0.11, 0.04, z + 0.07, '#a07a4a'); P.box(x - 0.1, 0.04, z - 0.065, x + 0.1, 0.12, z + 0.065, '#a65a43'); }   // bricks on pallets
  P.cyl(-m + 0.2, hd * 0.25, 0.26, 0.02, SLAB, 0.2, 10, '#d8c79a');   // sand
  for (let i = 0; i < 4; i++) P.box(m - 0.55, SLAB + i * 0.026, n * 0.15 - 0.2 + (i % 2) * 0.01, m - 0.05, SLAB + (i + 1) * 0.026, n * 0.15 + 0.2 + (i % 2) * 0.01, i % 2 ? '#b98f5a' : '#c9a06a');   // stacked timber
  for (let i = 0; i < 5; i++) P.cyl(-m + 0.78 + (i % 3) * 0.075, -n + 0.3 + (i >> 1) * 0.05, 0.034, 0.034, SLAB, 0.07, 8, '#3d6e9c');   // pipes on end
  for (const x of [-0.5, -0.3, 0.3, 0.5]) P.cyl(x, hd - 0.18, 0.03, 0.008, SLAB, 0.09, 6, '#e8742a');   // cones along the gate
}
