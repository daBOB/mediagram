// Small helpers shared by the bake page: a seeded random, canvases, and talking to the bake driver.
export let seed = 7;
export const rand = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
export const reseed = (s) => { seed = s; };
export const canvas = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };
export const post = (path, body) => fetch(path, { method: 'POST', body });
export const log = (...a) => post('/log', a.join(' '));
export const png = (c) => new Promise((ok, fail) => c.toBlob((b) => (b ? ok(b) : fail(new Error('toBlob'))), 'image/png'));
// a canvas as WebP bytes (alpha is kept, and lossless in Chrome's encoder)
export const webp = (c, q = 0.86) => new Promise((ok, fail) => c.toBlob((b) => (b ? ok(b) : fail(new Error('toBlob'))), 'image/webp', q));
export async function save(name, blob, query = '') {
  const r = await post(`/out/${name}${query}`, blob);
  if (!r.ok) throw new Error(`could not save ${name}: ${r.status}`);
}
