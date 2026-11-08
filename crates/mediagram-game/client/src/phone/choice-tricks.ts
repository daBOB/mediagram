// Kopfnuss tricks on a phone's answer buttons, as pure functions of server
// time so every redraw agrees with the last one and they can be tested.

/** Wechselbad: the buttons move to new places this often. */
export const SWAP_MS = 1500;
/** Schneckenpost: answer texts are complete after this long. */
export const REVEAL_MS = 4000;
/** Kleber: an answer has to be held this long to count. */
export const HOLD_MS = 1000;

/** Cheap deterministic hash, so a swap looks random but repeats on redraw. */
function hash(n: number): number {
  let h = Math.imul(n ^ 0x9e3779b9, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  return (h ^ (h >>> 16)) >>> 0;
}

/**
 * Which option sits in each slot: `order[slot] = option index`. The identity
 * until the first swap, then a new arrangement every `SWAP_MS`, never the
 * same twice in a row.
 */
export function slotOrder(n: number, opensAt: number, now: number): number[] {
  const swap = Math.max(0, Math.floor((now - opensAt) / SWAP_MS));
  const identity = [...Array(n).keys()];
  if (swap === 0 || n < 2) return identity;
  // Rotate by 1..n-1 (never a no-op), then swap one pair for variety.
  const seed = hash(Math.floor(opensAt) * 31 + swap);
  const shift = 1 + (seed % (n - 1));
  const order = identity.map((i) => (i + shift) % n);
  const a = (seed >>> 8) % n;
  const b = (a + 1) % n;
  if (n > 2) [order[a], order[b]] = [order[b], order[a]];
  // A swap that lands back where the last one was would look like nothing happened.
  const before = slotOrder(n, opensAt, now - SWAP_MS);
  return order.join() === before.join() ? order.reverse() : order;
}

/** Schneckenpost: the part of `label` visible `now`. */
export function revealed(label: string, opensAt: number, now: number): string {
  const share = Math.min(1, Math.max(0, (now - opensAt) / REVEAL_MS));
  return label.slice(0, Math.ceil(label.length * share));
}

export function holdDone(startedAt: number, now: number): boolean {
  return now - startedAt >= HOLD_MS;
}
