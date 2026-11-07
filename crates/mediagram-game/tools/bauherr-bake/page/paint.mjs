// Painting faces into the atlas: real photo textures (Poly Haven, CC0) as patterns at a set size, windows,
// facades floor by floor, roofs, signs and awnings. Coordinates are face units, y down from the face's top.
// Lit texels get their alpha cut (alpha < 1 = self-lit, see townMaterial); everything else stays opaque.
import { rand } from './util.mjs';

export const TEX = {};
// the face being painted (set by Atlas.draw); lit windows and signs note themselves there for their halos
export const litLog = { rect: null };
const image = (url) => new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => fail(new Error(url)); i.src = url; });
export const TEXTURES = ['plastered_wall_04', 'large_red_bricks', 'sandstone_blocks_05', 'stone_wall', 'clay_roof_tiles_02', 'roof_slates_02',
  'weathered_plank_siding', 'cobblestone_square', 'cobblestone_floor_001', 'asphalt_02', 'sparse_grass', 'forest_leaves_02', 'gravel_floor', 'square_tiles', 'wood_floor_deck',
  'rock_face_03', 'rocky_terrain', 'snow_02', 'seaside_rock', 'grass_ground', 'park_dirt'];
export async function loadTextures() { await Promise.all(TEXTURES.concat(NORMALS).map((n) => image(`/tex/${n}.jpg`).then((i) => { TEX[n] = i; }))); }
// roof normal maps (Poly Haven nor_gl), baked into the atlases as relief under the look's sun (Atlas.relief)
export const NORMALS = ['clay_roof_tiles_02-normal', 'roof_slates_02-normal'];

// Blend modes over big areas stall the box CPU, so every tinted surface is composed once into a 512 px tile
// and then laid as a plain pattern. draw(g) paints the tile; the key names it.
const tiles = new Map();
export function cached(key, draw) {
  if (!tiles.has(key)) { const c = document.createElement('canvas'); c.width = c.height = 512; draw(c.getContext('2d')); tiles.set(key, c); }
  return tiles.get(key);
}
// the photo, multiplied by a tint
export const tinted = (name, css) => cached(`${name}*${css ?? ''}`, (g) => { g.drawImage(TEX[name], 0, 0, 512, 512); if (css) tint(g, 0, 0, 512, 512, css); });
// a colour with the photo laid over it soft-light (lawn, foliage) or overlay (plaster): any colour keeps a real surface
export const over = (name, css, mode = 'soft-light', n = 1) => cached(`${name}~${css}~${mode}~${n}`, (g) => {
  g.fillStyle = css; g.fillRect(0, 0, 512, 512); g.globalCompositeOperation = mode; for (let i = 0; i < n; i++) g.drawImage(TEX[name], 0, 0, 512, 512);
});
// fill a rect with a tile (a photo name or a composed canvas), one repeat = `tile` units; rot turns it
export function photo(g, name, x, y, w, h, tile, o = {}) {
  const img = typeof name === 'string' ? tinted(name, o.tint) : name, p = g.createPattern(img, 'repeat');
  p.setTransform(new DOMMatrix().translate(x + (o.ox ?? rand() * tile), y + (o.oy ?? rand() * tile)).rotate(o.rot ?? 0).scale(tile / img.width));
  g.save(); g.fillStyle = p; g.globalAlpha = o.alpha ?? 1; g.fillRect(x, y, w, h); g.restore();
}
export function tint(g, x, y, w, h, css, op = 'multiply') { g.save(); g.globalCompositeOperation = op; g.fillStyle = css; g.fillRect(x, y, w, h); g.restore(); }
// plaster: the colour with the plaster photo over it and a trace of stone, one tile per colour
export function plaster(g, x, y, w, h, css, tile = 1.2) {
  const t = cached(`plaster~${css}`, (c) => {
    c.fillStyle = css; c.fillRect(0, 0, 512, 512);
    c.globalCompositeOperation = 'overlay'; c.globalAlpha = 0.9; c.drawImage(TEX.plastered_wall_04, 0, 0, 512, 512);
    c.globalCompositeOperation = 'multiply'; c.globalAlpha = 0.06; c.drawImage(TEX.sandstone_blocks_05, 0, 0, 512, 512);
  });
  photo(g, t, x, y, w, h, tile);
}
export function material(g, m, x, y, w, h) {
  if (m.mat === 'plaster') plaster(g, x, y, w, h, m.tint, m.tile);
  else photo(g, { brick: 'large_red_bricks', sandstone: 'sandstone_blocks_05', stone: 'stone_wall', timber: 'weathered_plank_siding', slate: 'roof_slates_02', clay: 'clay_roof_tiles_02', deck: 'wood_floor_deck', gravel: 'gravel_floor', tiles: 'square_tiles', leaves: 'forest_leaves_02', grass: 'sparse_grass', cobble: 'cobblestone_square' }[m.mat], x, y, w, h, m.tile ?? 0.6, { tint: m.tint, rot: m.rot, ox: m.ox, oy: m.oy });
}
// cut the alpha: these texels give their own light (k 0..0.8)
export function glow(g, draw, k) { g.save(); g.globalCompositeOperation = 'destination-out'; g.fillStyle = `rgba(0,0,0,${k})`; g.strokeStyle = g.fillStyle; draw(); g.restore(); }
export function shade(g, x, y, w, h, from, to) {   // vertical darkening gradient (contact shadow, eaves)
  const gr = g.createLinearGradient(0, y, 0, y + h); gr.addColorStop(0, from); gr.addColorStop(1, to); g.fillStyle = gr; g.fillRect(x, y, w, h);
}

const WARM = [['#ffe2b0', '#f2a95c'], ['#ffd59a', '#e8913f'], ['#fff0d0', '#f5c27a'], ['#d8e6ff', '#9db8e6']];
// one window, top-left (x, y), w × h; o: { frame, lit, mull, arch, shutters, ped, sill, door, glowK }
export function windowAt(g, x, y, w, h, o) {
  if (o.far) {   // a distant house: frame and pane as flat fills (a few px on screen; gradients only cost load time)
    g.fillStyle = o.frame; g.fillRect(x, y, w, h); g.fillStyle = o.lit ? WARM[o.lit - 1][0] : '#2c333d'; g.fillRect(x + w * 0.14, y + h * 0.12, w * 0.72, h * 0.76);
    if (o.lit) { glow(g, () => g.fillRect(x + w * 0.14, y + h * 0.12, w * 0.72, h * 0.76), o.glowK ?? 0.78); litLog.rect?.lit.push([x, y, w, h]); }
    return;
  }
  const f = Math.min(w, h) * 0.12, arch = o.arch ? w / 2 : 0;
  const shape = (inset) => { g.beginPath(); if (arch) { g.moveTo(x + inset, y + h - inset); g.lineTo(x + inset, y + arch); g.arc(x + w / 2, y + arch, w / 2 - inset, Math.PI, 0); g.lineTo(x + w - inset, y + h - inset); g.closePath(); } else g.rect(x + inset, y + inset, w - 2 * inset, h - 2 * inset); };
  if (o.shutters) { g.fillStyle = o.shutters; for (const sx of [x - w * 0.52, x + w * 1.02]) { g.fillRect(sx, y, w * 0.5, h); g.fillStyle = 'rgba(0,0,0,0.25)'; for (let k = 1; k < 6; k++) g.fillRect(sx, y + (h * k) / 6, w * 0.5, h * 0.025); g.fillStyle = o.shutters; } }
  g.fillStyle = 'rgba(20,16,12,0.55)'; shape(-f * 0.6); g.fill();                     // reveal: the deep window opening
  g.fillStyle = o.frame; shape(0); g.fill();
  if (o.lit) {
    const [a, b] = WARM[o.lit - 1], gr = g.createLinearGradient(0, y, 0, y + h); gr.addColorStop(0, a); gr.addColorStop(1, b);
    g.fillStyle = gr; shape(f); g.fill();
    g.fillStyle = 'rgba(120,60,20,0.35)'; g.fillRect(x + f, y + f, w * 0.18, h - 2 * f); g.fillRect(x + w - f - w * 0.18, y + f, w * 0.18, h - 2 * f);   // curtains
    glow(g, () => { shape(f); g.fill(); }, o.glowK ?? 0.78); litLog.rect?.lit.push([x, y, w, h]);
  } else {
    const gr = g.createLinearGradient(0, y, 0, y + h); gr.addColorStop(0, o.glass?.[0] ?? '#3c4552'); gr.addColorStop(1, o.glass?.[1] ?? '#1e232b');
    g.fillStyle = gr; shape(f); g.fill();
    g.fillStyle = 'rgba(230,225,215,0.18)'; g.fillRect(x + f, y + f, w * 0.22, h - 2 * f);   // a drawn curtain, so empty rooms do not read as holes
  }
  g.fillStyle = o.frame; const m = f * 0.55;
  if (o.mull !== 'none') { g.fillRect(x + w / 2 - m / 2, y + f, m, h - 2 * f); if (!o.door) g.fillRect(x + f, y + Math.max(arch, h * 0.36), w - 2 * f, m); }
  if (o.sill !== false) { g.fillStyle = o.sillColor ?? '#d9d2c4'; g.fillRect(x - f * 0.8, y + h, w + f * 1.6, f * 0.9); g.fillStyle = 'rgba(0,0,0,0.25)'; g.fillRect(x - f * 0.6, y + h + f * 0.9, w + f * 1.2, f * 0.7); }
  if (o.ped === 'tri') { g.fillStyle = o.pedColor ?? o.frame; g.beginPath(); g.moveTo(x - f * 1.4, y - f * 0.6); g.lineTo(x + w / 2, y - h * 0.32); g.lineTo(x + w + f * 1.4, y - f * 0.6); g.closePath(); g.fill(); g.fillStyle = 'rgba(0,0,0,0.22)'; g.fillRect(x - f, y - f * 0.6, w + 2 * f, f * 0.6); }
  else if (o.ped === 'flat') { g.fillStyle = o.pedColor ?? o.frame; g.fillRect(x - f * 1.4, y - f * 2.2, w + f * 2.8, f * 1.4); g.fillStyle = 'rgba(0,0,0,0.25)'; g.fillRect(x - f, y - f * 0.8, w + 2 * f, f * 0.6); }
}

// a facade, floor by floor. spec: { mat, tint, tile, plinth, groundH, floorH, top, win, pitch, groundWin, bands, fachwerk, lit, door, doorColor }
export function facade(spec, face = {}) {
  return (g, wu, hu) => {
    material(g, spec, -0.1, -0.1, wu + 0.2, hu + 0.2);
    const gh = spec.groundH ?? spec.floorH, fh = spec.floorH, top = spec.top ?? 0.08, n = Math.max(0, Math.floor((hu - gh - top + 0.02) / fh));
    if (spec.plinth) { material(g, spec.plinth, 0, hu - spec.plinth.h, wu, spec.plinth.h + 0.1); g.fillStyle = 'rgba(0,0,0,0.25)'; g.fillRect(0, hu - spec.plinth.h - 0.006, wu, 0.008); }
    const cols = Math.max(1, Math.round((wu - 0.12) / (spec.pitch ?? 0.3))), step = (wu - 0.08) / cols;
    const lit = (k) => (rand() < (spec.lit ?? 0) * k ? (rand() < 0.15 ? 4 : 1 + Math.floor(rand() * 3)) : 0);
    // upper floors, top down from the attic band
    for (let f = 0; f < n; f++) {
      const yb = hu - gh - f * fh, w = spec.win;
      if (spec.fachwerk) fachwerk(g, spec.fachwerk, 0, yb - fh, wu, fh, cols, step, w);
      if (spec.bands && f > 0) { g.fillStyle = spec.bandColor ?? 'rgba(255,250,240,0.55)'; g.fillRect(0, yb - 0.012, wu, 0.014); g.fillStyle = 'rgba(0,0,0,0.18)'; g.fillRect(0, yb + 0.002, wu, 0.006); }
      for (let c = 0; c < cols; c++) {
        const ww = w.w * (f === 0 && spec.nobile ? 1.05 : 1), wh = w.h * (f === 0 && spec.nobile ? 1.15 : 1);
        windowAt(g, 0.04 + step * (c + 0.5) - ww / 2, yb - fh * 0.5 - wh / 2 - fh * 0.04, ww, wh, { ...w, ped: f === 0 && spec.nobile ? spec.nobile : w.ped, lit: lit(1), glowK: spec.glowK, far: spec.far });
      }
    }
    // ground floor: a door in the middle of the front, windows (or shop windows) elsewhere
    const gw = spec.groundWin ?? spec.win, door = face.door ? Math.floor(cols / 2) : -1;
    for (let c = 0; c < cols; c++) {
      const cx = 0.04 + step * (c + 0.5);
      if (c === door) {
        const dw = Math.min(step * 0.62, 0.16), dh = gh * 0.72;
        windowAt(g, cx - dw / 2, hu - dh - (spec.plinth ? 0.01 : 0), dw, dh, { frame: spec.doorFrame ?? '#e8e1d4', mull: 'cross', door: true, arch: spec.doorArch, sill: false, lit: (spec.lit ?? 0) > 0.05 ? 2 : 0, glass: ['#4a3a2a', '#2a2018'], glowK: spec.glowK });
        continue;
      }
      windowAt(g, cx - gw.w / 2, hu - gh * 0.5 - gw.h / 2 - 0.01, gw.w, gw.h, { ...gw, lit: lit(2.2), glowK: spec.glowK, far: spec.far });
    }
    if (spec.cornice !== false) { g.fillStyle = spec.bandColor ?? 'rgba(255,250,240,0.6)'; g.fillRect(0, 0, wu, top * 0.45); g.fillStyle = 'rgba(0,0,0,0.3)'; g.fillRect(0, top * 0.45, wu, 0.012); }
    shade(g, 0, hu - 0.14, wu, 0.14, 'rgba(0,0,0,0)', 'rgba(10,8,6,0.35)');   // dirt and contact shadow at the foot
    shade(g, 0, 0, wu, 0.07, 'rgba(10,8,6,0.35)', 'rgba(0,0,0,0)');          // under the eaves
  };
}
// half-timbering on one floor: sill and head beams, posts beside the windows, braces in the blank bays
function fachwerk(g, fw, x, y, w, h, cols, step, win) {
  g.save(); g.strokeStyle = fw.color; g.lineWidth = fw.beam ?? 0.022; g.lineCap = 'square';
  const L = (x0, y0, x1, y1) => { g.beginPath(); g.moveTo(x0, y0); g.lineTo(x1, y1); g.stroke(); };
  L(x, y + h - 0.012, x + w, y + h - 0.012); L(x, y + 0.012, x + w, y + 0.012);
  for (let c = 0; c <= cols; c++) L(0.04 + step * c, y, 0.04 + step * c, y + h);
  for (let c = 0; c < cols; c++) {
    const x0 = 0.04 + step * c, cx = x0 + step / 2;
    L(cx - win.w / 2 - 0.016, y, cx - win.w / 2 - 0.016, y + h); L(cx + win.w / 2 + 0.016, y, cx + win.w / 2 + 0.016, y + h);
    if (c % 3 === 0) { L(x0, y + h, cx - win.w / 2 - 0.016, y + h * 0.35); L(x0 + step, y + h, cx + win.w / 2 + 0.016, y + h * 0.35); }
  }
  g.restore();
}

// The roof tiles' relief, baked: the tinted tile photo lit texel by texel through its normal map under the look's
// sun, relative to the flat roof (whose light the vertex colours carry). One 256 px tile per material and roof
// orientation (rounded), so the atlas itself is never read back.
let LIGHT = null;
export const setLight = (l) => { LIGHT = l; };
const pixels = (src, n) => { const c = document.createElement('canvas'); c.width = c.height = n; const g = c.getContext('2d', { willReadFrequently: true }); g.drawImage(src, 0, 0, n, n); return [c, g, g.getImageData(0, 0, n, n)]; };
function reliefTile(spec, face) {
  const nm = { clay: 'clay_roof_tiles_02-normal', slate: 'roof_slates_02-normal' }[spec.mat];
  if (!nm || !face || !LIGHT || spec.far) return null;
  const cr = Math.cos(face.P.rot), sr = Math.sin(face.P.rot), w = (v) => [v.x * cr + v.z * sr, v.y, -v.x * sr + v.z * cr];
  const R = w(face.right), U = w(face.up), N = w(face.n), key = `relief|${spec.mat}|${spec.tint}|${[...R, ...U, ...N].map((v) => v.toFixed(1))}`;
  return cached(key, (out) => {
    // only the sun term changes with the texel's normal: light = ambient + sun · max(0, n·L), relative to the flat roof
    const n = 256, [, , nd] = pixels(TEX[nm], n), [c, g, img] = pixels(tinted(spec.mat === 'clay' ? 'clay_roof_tiles_02' : 'roof_slates_02', spec.tint), n), px = img.data;
    const L = LIGHT.L, S = LIGHT.sun, flat = LIGHT.at(...N).slice(), d0 = Math.max(0, N[0] * L.x + N[1] * L.y + N[2] * L.z), amb = flat.map((v, k) => Math.max(0.02, v - S[k] * d0));
    const lr = R[0] * L.x + R[1] * L.y + R[2] * L.z, lu = U[0] * L.x + U[1] * L.y + U[2] * L.z, ln = d0;   // L in the roof's own frame
    for (let i = 0; i < n * n * 4; i += 4) {
      const nx = (nd.data[i] / 127.5 - 1) * 0.8, ny = (nd.data[i + 1] / 127.5 - 1) * 0.8, nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
      const d = Math.max(0, nx * lr + ny * lu + nz * ln);
      for (let k = 0; k < 3; k++) px[i + k] = Math.min(255, px[i + k] * Math.sqrt((amb[k] + S[k] * d) / flat[k]));   // sqrt ≈ back to sRGB
    }
    g.putImageData(img, 0, 0); out.drawImage(c, 0, 0, 512, 512);
  });
}
// a roof plane: tiles running down the slope, a little moss and soot, darker under the ridge and at the eaves
export function roof(spec) {
  return (g, wu, hu, r) => {
    const relief = reliefTile(spec, r?.face);
    if (relief) photo(g, relief, -0.05, -0.05, wu + 0.1, hu + 0.1, spec.tile ?? 0.6, { rot: spec.rot });
    else material(g, spec, -0.05, -0.05, wu + 0.1, hu + 0.1);
    for (let i = 0; i < 6; i++) { const x = rand() * wu, y = rand() * hu, r = 0.08 + rand() * 0.2, gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, `rgba(${rand() < 0.5 ? '60,70,30' : '30,25,20'},0.16)`); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(x - r, y - r, 2 * r, 2 * r); }
    shade(g, 0, 0, wu, 0.05, 'rgba(0,0,0,0.3)', 'rgba(0,0,0,0)');
    shade(g, 0, hu - 0.04, wu, 0.04, 'rgba(0,0,0,0)', 'rgba(0,0,0,0.25)');
  };
}
// a hotel name on a dark board, letters in the owner's colour; lit: letters glow
export function sign(text, color, lit, o = {}) {
  return (g, wu, hu) => {
    g.fillStyle = o.bg ?? '#1b1d22'; g.fillRect(-0.05, -0.05, wu + 0.1, hu + 0.1);
    const px = 200, s = wu / px; g.save(); g.scale(s, s);
    const H = hu / s; g.font = `800 ${H * 0.66}px "Bricolage Grotesque", Roboto, sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillStyle = color; g.fillText(text, px / 2, H * 0.54, px * 0.94);
    if (lit) { glow(g, () => g.fillText(text, px / 2, H * 0.54, px * 0.94), lit); litLog.rect?.lit.push([wu * 0.15, hu * 0.3, wu * 0.7, hu * 0.4, color]); }
    g.restore();
  };
}
export function stripes(color, n = 8) {
  return (g, wu, hu) => { for (let i = 0; i < n; i++) { g.fillStyle = i % 2 ? '#efe7d6' : color; g.fillRect((i * wu) / n - 0.01, -0.05, wu / n + 0.02, hu + 0.1); } shade(g, 0, hu * 0.7, wu, hu * 0.3, 'rgba(0,0,0,0)', 'rgba(0,0,0,0.25)'); };
}
export const flat = (m) => (g, wu, hu) => material(g, m, -0.05, -0.05, wu + 0.1, hu + 0.1);
// a pool seen from above: tiled basin under still, matte water, darker towards the walls
export function poolWater(g, wu, hu) {
  photo(g, 'square_tiles', -0.05, -0.05, wu + 0.1, hu + 0.1, 0.35, { tint: '#5fc2c9' });
  g.fillStyle = 'rgba(30,120,140,0.55)'; g.fillRect(-0.05, -0.05, wu + 0.1, hu + 0.1);
  const e = Math.min(wu, hu) * 0.25, gr = g.createLinearGradient(0, 0, 0, hu); gr.addColorStop(0, 'rgba(5,40,60,0.35)'); gr.addColorStop(0.3, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(5,40,60,0.15)');
  g.fillStyle = gr; g.fillRect(0, 0, wu, hu); g.strokeStyle = 'rgba(5,40,60,0.25)'; g.lineWidth = e * 0.25; g.strokeRect(0, 0, wu, hu);
  g.strokeStyle = 'rgba(255,255,255,0.10)'; g.lineWidth = 0.008;   // the lane lines on the basin floor
  for (let i = 1; i < 4; i++) { g.beginPath(); g.moveTo(0.1, (hu * i) / 4); g.lineTo(wu - 0.1, (hu * i) / 4); g.stroke(); }
}
