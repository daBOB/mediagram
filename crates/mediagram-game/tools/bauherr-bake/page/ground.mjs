// The ground print: lawn, the landscape zones, water, the winding road with its kerbs and pavements, the fields set
// into it, the plaza, each lot's forecourt, and the baked light (sun shadows of everything that stands still).
import { GROUND as G, FIELDS, FIELD_KINDS, LOTS, FIELD_SIZE, ROAD_WIDTH, roadLine, PLAZA, LOT_W, LOT_D } from '../../../client/src/tv/bauherr/bauherr-layout.ts';
import { TEX, over } from './paint.mjs';
import { rand, canvas } from './util.mjs';
import { shadowLayer } from './shadow-layer.mjs';
import { icon } from './field-icons.mjs';
import { LAKE, SEA, CREEK, CREEK_W, POND, ZONES } from './scenery-map.mjs';
import { GS, look } from './look.mjs';

export const BW = Math.round((G.x1 - G.x0) * GS), BH = Math.round((G.z1 - G.z0) * GS);
export const px = (x) => (x - G.x0) * GS, pz = (z) => (z - G.z0) * GS;
const KIND = { B: ['bank', '#d9ad55'], R: ['rathaus', '#8d9fbf'], P: ['bauplatz', '#8bb174'], S: ['baustelle', '#d9824a'], F: ['eingang', '#b48ac0'] };
// a photo texture (or composed tile) as a pattern, `tile` world units per repeat
export function pat(g, src, tile, rot = 0) { const img = typeof src === 'string' ? TEX[src] : src, p = g.createPattern(img, 'repeat'); p.setTransform(new DOMMatrix().rotate(rot).scale((tile * GS) / img.width)); return p; }
const poly = (g, pts) => { g.beginPath(); pts.forEach(([x, z], i) => (i ? g.lineTo(px(x), pz(z)) : g.moveTo(px(x), pz(z)))); g.closePath(); };
// a smooth line through points (quadratic curves between midpoints)
function curve(g, pts) {
  g.beginPath(); g.moveTo(px(pts[0][0]), pz(pts[0][1]));
  for (let i = 1; i + 1 < pts.length; i++) g.quadraticCurveTo(px(pts[i][0]), pz(pts[i][1]), px((pts[i][0] + pts[i + 1][0]) / 2), pz((pts[i][1] + pts[i + 1][1]) / 2));
  const l = pts[pts.length - 1]; g.lineTo(px(l[0]), pz(l[1]));
}
const road = (g, width, style) => {
  const line = roadLine(0.25); g.lineWidth = width * GS; g.strokeStyle = style; g.lineJoin = 'round'; g.lineCap = 'butt';
  g.beginPath(); line.forEach((f, i) => (i ? g.lineTo(px(f.x), pz(f.z)) : g.moveTo(px(f.x), pz(f.z)))); g.closePath(); g.stroke();
};
const rectOf = (g, x0, z0, x1, z1) => g.rect(px(x0), pz(z0), (x1 - x0) * GS, (z1 - z0) * GS);
const ground = {
  forest: (g) => { g.fillStyle = pat(g, over('forest_leaves_02', '#6a6a44', 'soft-light', 2), 1.1); g.fill(); g.fillStyle = 'rgba(30,40,10,0.28)'; g.fill(); },
  city: (g) => { g.fillStyle = pat(g, 'cobblestone_floor_001', 0.9); g.fill(); g.fillStyle = 'rgba(210,200,190,0.35)'; g.fill(); },
  alpine: (g) => { g.fillStyle = pat(g, over('sparse_grass', '#8a9a62', 'soft-light', 2), 1.4); g.fill(); g.globalAlpha = 0.5; g.fillStyle = pat(g, 'gravel_floor', 1.2); g.fill(); g.globalAlpha = 1; },
  orchard: (g) => { g.fillStyle = pat(g, over('sparse_grass', '#86a455', 'soft-light', 2), 1.6); g.fill(); },
  park: (g) => { g.fillStyle = pat(g, over('sparse_grass', '#80a050', 'soft-light', 2), 1.6); g.fill(); },
  shore: (g) => { g.fillStyle = pat(g, over('sparse_grass', '#7a9a58', 'soft-light', 2), 1.6); g.fill(); },
};
// water: a gravel shore under it, still matte teal darker towards the middle, and a foam line where asked
function water(g, outline, foam) {
  g.save(); g.lineJoin = 'round';
  poly(g, outline); g.lineWidth = 0.9 * GS; g.strokeStyle = pat(g, 'gravel_floor', 0.8); g.stroke(); g.lineWidth = 1.3 * GS; g.strokeStyle = 'rgba(190,170,130,0.35)'; g.stroke();
  poly(g, outline); g.fillStyle = '#3f7b82'; g.fill(); g.globalAlpha = 0.18; g.fillStyle = pat(g, 'square_tiles', 0.5); g.fill(); g.globalAlpha = 1;
  g.clip(); g.lineWidth = 1.6 * GS; g.strokeStyle = 'rgba(120,200,190,0.35)'; poly(g, outline); g.stroke(); g.lineWidth = 0.5 * GS; g.strokeStyle = 'rgba(210,240,235,0.55)'; poly(g, outline); g.stroke();
  if (foam) { g.lineWidth = 0.12 * GS; g.strokeStyle = 'rgba(255,255,255,0.7)'; g.setLineDash([0.35 * GS, 0.2 * GS]); poly(g, outline); g.stroke(); }
  g.restore();
}
function creek(g) {
  g.save(); g.lineCap = 'round'; g.lineJoin = 'round';
  curve(g, CREEK); g.lineWidth = (CREEK_W + 0.9) * GS; g.strokeStyle = pat(g, 'gravel_floor', 0.8); g.stroke();
  curve(g, CREEK); g.lineWidth = CREEK_W * GS; g.strokeStyle = '#3f7b82'; g.stroke();
  curve(g, CREEK); g.lineWidth = CREEK_W * 0.4 * GS; g.strokeStyle = 'rgba(190,230,225,0.35)'; g.stroke();
  g.beginPath(); g.ellipse(px(POND.x), pz(POND.z), (POND.rx + 0.45) * GS, (POND.rz + 0.45) * GS, 0, 0, 7); g.fillStyle = pat(g, 'gravel_floor', 0.8); g.fill();
  g.beginPath(); g.ellipse(px(POND.x), pz(POND.z), POND.rx * GS, POND.rz * GS, 0, 0, 7); g.fillStyle = '#3f7b82'; g.fill();
  g.restore();
}
function lotGround(g, lot) {
  g.save(); g.translate(px(lot.x), pz(lot.z)); g.rotate(-lot.rot);
  const w = LOT_W * GS, d = LOT_D * GS;
  g.fillStyle = pat(g, 'cobblestone_square', 0.4); g.fillRect(-w / 2, d * 0.04, w, d * 0.46);   // the forecourt
  g.fillStyle = 'rgba(236,226,206,0.5)'; g.fillRect(-w / 2, d * 0.04, w, d * 0.46);
  g.fillStyle = pat(g, over('sparse_grass', '#7f9c56', 'soft-light', 2), 1.4); g.fillRect(-w / 2, -d / 2, w, d * 0.54);
  g.fillStyle = pat(g, 'gravel_floor', 0.8); g.fillRect(-0.45 * GS, -d * 0.1, 0.9 * GS, d * 0.6);   // the way to the door
  g.fillStyle = 'rgba(214,196,160,0.5)'; g.fillRect(-0.45 * GS, -d * 0.1, 0.9 * GS, d * 0.6);
  g.restore();
}
function fields(g) {
  FIELDS.forEach((f, i) => {
    const s = FIELD_SIZE * GS, k = FIELD_KINDS[i], [kind, tintCss] = KIND[k] ?? [null, null];
    g.save(); g.translate(px(f.x), pz(f.z)); g.rotate(f.ang);
    g.fillStyle = '#26262a'; g.fillRect(-s / 2 - 5, -s / 2 - 5, s + 10, s + 10);
    g.fillStyle = pat(g, 'cobblestone_square', 0.42); g.fillRect(-s / 2, -s / 2, s, s);
    g.fillStyle = kind ? tintCss : 'rgba(236,230,216,0.55)'; g.globalCompositeOperation = kind ? 'multiply' : 'source-over'; g.fillRect(-s / 2, -s / 2, s, s);
    g.globalCompositeOperation = 'source-over';
    if (kind) { g.fillStyle = 'rgba(255,245,225,0.25)'; g.fillRect(-s / 2, -s / 2, s, s); }
    g.strokeStyle = 'rgba(255,250,235,0.35)'; g.lineWidth = 4; g.strokeRect(-s / 2 + 2, -s / 2 + 2, s - 4, s - 4);
    if (kind) { g.rotate(-f.ang); g.translate(3, 4); icon(g, kind, s * 0.6, 'rgba(30,24,18,0.55)'); g.translate(-3, -4); icon(g, kind, s * 0.6, '#f6efe0'); }
    g.restore();
  });
}
/** sh: static shadow casters { polys, trees, lines, ao }; lamps: [x, z] */
export function paintGround(light, sh) {
  const c = canvas(BW, BH), g = c.getContext('2d');
  g.fillStyle = pat(g, over('sparse_grass', look.grass, 'soft-light', 2), 1.6); g.fillRect(0, 0, BW, BH);
  for (let i = 0; i < 90; i++) { const x = rand() * BW, y = rand() * BH, r = (0.8 + rand() * 2) * GS, gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, rand() < 0.5 ? 'rgba(210,200,120,0.10)' : 'rgba(20,40,10,0.12)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(x - r, y - r, 2 * r, 2 * r); }
  for (const [x0, z0, x1, z1, kind] of ZONES) { g.beginPath(); rectOf(g, x0, z0, x1, z1); ground[kind](g); }
  g.fillStyle = pat(g, 'gravel_floor', 0.8); g.fillRect(px(3.9), pz(-4.0), 4.1 * GS, 0.7 * GS);   // the spa park's walk
  water(g, LAKE, false); water(g, SEA, true); creek(g);
  // the plaza: cobbled, a paved border
  g.fillStyle = '#bdb8ae'; g.fillRect(px(PLAZA.x0) - 6, pz(PLAZA.z0) - 6, (PLAZA.x1 - PLAZA.x0) * GS + 12, (PLAZA.z1 - PLAZA.z0) * GS + 12);
  g.fillStyle = pat(g, 'cobblestone_floor_001', 0.7); g.fillRect(px(PLAZA.x0), pz(PLAZA.z0), (PLAZA.x1 - PLAZA.x0) * GS, (PLAZA.z1 - PLAZA.z0) * GS); g.fillStyle = 'rgba(240,230,210,0.2)'; g.fillRect(px(PLAZA.x0), pz(PLAZA.z0), (PLAZA.x1 - PLAZA.x0) * GS, (PLAZA.z1 - PLAZA.z0) * GS);
  for (const lot of LOTS) lotGround(g, lot);
  road(g, ROAD_WIDTH + 0.55, pat(g, 'cobblestone_square', 0.55)); road(g, ROAD_WIDTH + 0.55, 'rgba(235,228,214,0.45)');
  road(g, ROAD_WIDTH + 0.1, '#bdb8ae'); road(g, ROAD_WIDTH, pat(g, 'asphalt_02', 1.4)); road(g, ROAD_WIDTH, look.asphalt);
  fields(g);
  g.save(); g.globalCompositeOperation = 'multiply'; g.imageSmoothingQuality = 'high';
  g.drawImage(shadowLayer(BW, BH, (x, z) => [px(x), pz(z)], sh, light, look, 2), 0, 0, BW, BH); g.restore();
  return c;
}

/** The land beyond the print, at a few pixels per unit: lawn and the same water, fading into the haze. */
export const OUTER = { x0: -120, x1: 120, z0: -100, z1: 60, px: 6 };
export function paintOuter() {
  const { x0, x1, z0, z1, px: k } = OUTER, c = canvas((x1 - x0) * k, (z1 - z0) * k), g = c.getContext('2d');
  g.fillStyle = '#7d9156'; g.fillRect(0, 0, c.width, c.height);
  for (let i = 0; i < 400; i++) { const x = rand() * c.width, y = rand() * c.height, r = (3 + rand() * 12) * k, gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, rand() < 0.5 ? 'rgba(200,190,110,0.10)' : 'rgba(30,50,15,0.12)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(x - r, y - r, 2 * r, 2 * r); }
  g.save(); g.scale(k, k); g.translate(-x0, -z0); g.lineJoin = 'round';
  for (const [outline, edge] of [[LAKE, '#bfae8a'], [SEA, '#c9b793']]) {
    g.beginPath(); outline.forEach(([x, z], i) => (i ? g.lineTo(x, z) : g.moveTo(x, z))); g.closePath();
    g.lineWidth = 1.6; g.strokeStyle = edge; g.stroke(); g.fillStyle = '#3f7b82'; g.fill();
  }
  g.restore();
  return c;
}
