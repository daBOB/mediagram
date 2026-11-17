/**
 * Ambient's colour of a poster, the same pick the Android app makes
 * (`AmbientGlow.kt`: `strongestColourOf`, `glowOf`), so one poster glows in
 * one colour on every surface. Pixels are read from a decode a few dozen
 * pixels across; a browser's downscale averages where Android's decoder
 * does, so the two agree on the colour family and may differ by a shade.
 */

const HUES = 12;
/** Less vivid weight than this per pixel, and a poster has no colour of its own to glow in. */
const COLOURLESS_BELOW = 0.02;
/** About this many pixels across is enough to know a poster's colour. */
const SAMPLE_WIDTH = 32;
const CACHE_LIMIT = 512;

// Android does this arithmetic in single precision; so does this, so a pixel on a family's edge lands in the same family.
const f32 = Math.fround;
const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi);

/** androidx `ColorUtils.colorToHSL`: hue in degrees, saturation and lightness in 0..1. */
export function toHsl(r, g, b) {
  const rf = f32(r / 255), gf = f32(g / 255), bf = f32(b / 255);
  const max = Math.max(rf, gf, bf), min = Math.min(rf, gf, bf);
  const delta = f32(max - min);
  const l = f32(f32(max + min) / 2);
  let h = 0, s = 0;
  if (max !== min) {
    if (max === rf) h = f32(f32(gf - bf) / delta) % 6;
    else if (max === gf) h = f32(f32(f32(bf - rf) / delta) + 2);
    else h = f32(f32(f32(rf - gf) / delta) + 4);
    s = f32(delta / f32(1 - Math.abs(f32(2 * l) - 1)));
  }
  h = f32(h * 60) % 360;
  if (h < 0) h += 360;
  return [clamp(h, 0, 360), clamp(s, 0, 1), clamp(l, 0, 1)];
}

/** androidx `ColorUtils.HSLToColor`, as [r, g, b]. */
export function fromHsl(h, s, l) {
  const c = f32(f32(1 - Math.abs(f32(2 * l) - 1)) * s);
  const m = f32(l - f32(0.5 * c));
  const x = f32(c * f32(1 - Math.abs((f32(h / 60) % 2) - 1)));
  const rgb = [[c + m, x + m, m], [x + m, c + m, m], [m, c + m, x + m], [m, x + m, c + m], [x + m, m, c + m], [c + m, m, x + m]];
  return rgb[Math.min(Math.trunc(h / 60), 5)].map((v) => clamp(Math.round(255 * v), 0, 255));
}

/**
 * A poster's strongest colour: its pixels sorted into twelve families of hue,
 * each weighed by how vivid it is (saturated, neither near black nor white),
 * and the heaviest family's own average. A poster with no colour to speak of
 * keeps its plain average, a grey it earns.
 * @param {ArrayLike<number>} rgba four bytes a pixel
 * @returns {number[]} [r, g, b]
 */
export function strongestColourOf(rgba) {
  const count = Math.floor(rgba.length / 4);
  if (count === 0) return [128, 128, 128];
  const weight = new Float64Array(HUES);
  const sum = [0, 1, 2].map(() => new Float64Array(HUES));
  const total = [0, 0, 0];
  for (let i = 0; i < count; i++) {
    const px = [rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2]];
    const [h, s, l] = toHsl(px[0], px[1], px[2]);
    const vivid = f32(s * f32(1 - Math.abs(f32(2 * l) - 1)));
    const family = clamp(Math.trunc(h / (360 / HUES)), 0, HUES - 1);
    weight[family] += vivid;
    px.forEach((v, k) => { total[k] += v; sum[k][family] += v * vivid; });
  }
  let best = 0;
  for (let k = 1; k < HUES; k++) if (weight[k] > weight[best]) best = k;
  if (weight[best] / count < COLOURLESS_BELOW) return total.map((v) => Math.trunc(v / count));
  return sum.map((per) => Math.trunc(per[best] / weight[best]));
}

/** A poster's colour made into a glow: saturated a little more, and kept in a band of lightness that reads on the page. */
export function glowOf([r, g, b]) {
  const [h, s, l] = toHsl(r, g, b);
  return fromHsl(h, Math.min(f32(f32(s * 1.3) + 0.08), 0.85), clamp(l, 0.42, 0.6));
}

const known = new Map();
let canvas = null;

/**
 * The glow of a loaded poster `<img>` as [r, g, b], worked out once per
 * picture and kept; `null` while it is not loaded, or if the browser will not
 * let its pixels be read.
 * @param {HTMLImageElement} img
 */
export function glowOfImage(img) {
  const src = img.currentSrc || img.src;
  if (known.has(src)) return known.get(src);
  if (!img.complete || !img.naturalWidth) return null;
  let sample = 1;
  while (Math.trunc(img.naturalWidth / (sample * 2)) >= SAMPLE_WIDTH) sample *= 2;
  const w = Math.ceil(img.naturalWidth / sample), h = Math.ceil(img.naturalHeight / sample);
  try {
    canvas ??= document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    context.drawImage(img, 0, 0, w, h);
    const glow = glowOf(strongestColourOf(context.getImageData(0, 0, w, h).data));
    if (known.size >= CACHE_LIMIT) known.delete(known.keys().next().value);
    known.set(src, glow);
    return glow;
  } catch {
    return null;
  }
}
