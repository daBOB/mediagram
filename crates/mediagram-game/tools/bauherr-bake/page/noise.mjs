// Smooth 2D gradient noise, for the shape and the colouring of the terrain: deterministic, so every bake gives the same land.
const P = new Uint8Array(512);
{
  let s = 1234567;
  const next = () => (s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
  const perm = Array.from({ length: 256 }, (_, i) => i);
  for (let i = 255; i > 0; i--) { const j = Math.floor(next() * (i + 1)); [perm[i], perm[j]] = [perm[j], perm[i]]; }
  for (let i = 0; i < 512; i++) P[i] = perm[i & 255];
}
const fade = (t) => t * t * t * (t * (t * 6 - 15) + 10);
const grad = (h, x, y) => { const a = (h & 7) * 0.7853981634; return Math.cos(a) * x + Math.sin(a) * y; };
/** Gradient noise in about -0.7 .. 0.7. */
export function noise(x, y) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi, X = xi & 255, Y = yi & 255, u = fade(xf), v = fade(yf);
  const a = P[P[X] + Y], b = P[P[X + 1] + Y], c = P[P[X] + Y + 1], d = P[P[X + 1] + Y + 1];
  const l0 = grad(a, xf, yf) + u * (grad(b, xf - 1, yf) - grad(a, xf, yf)), l1 = grad(c, xf, yf - 1) + u * (grad(d, xf - 1, yf - 1) - grad(c, xf, yf - 1));
  return l0 + v * (l1 - l0);
}
/** Layered noise, `n` octaves, each twice as fine and `gain` as strong; about -1 .. 1. */
export function fbm(x, y, n = 4, gain = 0.5) {
  let s = 0, a = 1, f = 1, t = 0;
  for (let i = 0; i < n; i++) { s += a * noise(x * f + i * 17.3, y * f - i * 9.1); t += a; a *= gain; f *= 2; }
  return (s / t) * 1.5;
}
/** Ridged noise, 0 .. 1: sharp crests where the layered noise crosses zero, as on rock ridges and gullies. */
export function ridged(x, y, n = 4) {
  let s = 0, a = 1, f = 1, t = 0, w = 1;
  for (let i = 0; i < n; i++) {
    const r = 1 - Math.min(1, Math.abs(noise(x * f + i * 5.7, y * f + i * 3.3)) * 1.8);
    const k = r * r * w; s += a * k; w = Math.min(1, k * 1.6); t += a; a *= 0.5; f *= 2.07;
  }
  return s / t;
}
export const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
