/**
 * When a title that started itself may actually begin.
 *
 * A viewer who pressed play is watching the screen and wants the picture; one
 * whose episode ended thirty seconds ago is not, and would rather the next one
 * arrive whole than arrive immediately and stop again ten seconds in. So an
 * unattended start waits for a buffer, and an asked-for one does not.
 *
 * The waiting has to be able to end even when the buffer never fills, which is
 * why there are three ways out rather than one.
 */

/** The buffer an unattended start waits for. */
export const READY_SECONDS = 60;

/**
 * How long it may wait for that before going anyway.
 *
 * A slow encoder should delay the next episode, not cancel it: giving up on
 * the wait still plays, it just plays with less in hand.
 */
export const PATIENCE_MS = 45_000;

/**
 * @param {{ahead: number, remaining: number|null, waitedMs: number}} at
 * @returns {boolean}
 */
export function autoplayReady(at) {
  const ahead = Number(at.ahead);
  if (Number.isFinite(ahead) && ahead >= READY_SECONDS) return true;

  // A forty second lesson can never hold a minute ahead of itself. Having all
  // of what is left is the same promise as having a minute of it.
  const left = Number(at.remaining ?? Number.NaN);
  if (Number.isFinite(left) && left > 0 && Number.isFinite(ahead) && ahead >= left - 1) return true;

  // Out of patience. Whatever is held is what it starts with.
  return Number(at.waitedMs) >= PATIENCE_MS;
}
