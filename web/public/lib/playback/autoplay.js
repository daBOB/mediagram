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

import { clockTime } from "../format.js";

/** The buffer an unattended start waits for. */
export const READY_SECONDS = 60;

/**
 * How long it may wait for that before going anyway.
 *
 * A slow encoder should delay the next episode, not cancel it: giving up on
 * the wait still plays, it just plays with less in hand.
 */
export const PATIENCE_MS = 45_000;

/** What the stage says while a title warms up: `Warming up · 0:12 of 1:00`. */
export function warmUpLine(ahead) {
  const held = Number.isFinite(ahead) ? Math.min(Math.max(ahead, 0), READY_SECONDS) : 0;
  return `Warming up · ${clockTime(held)} of ${clockTime(READY_SECONDS)}`;
}

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

/**
 * Whether the browser has stopped fetching with something held. A paused
 * video played directly loads a few seconds and stops (`suspend`), so the
 * minute never comes and waiting longer gains nothing: what it holds then is
 * all the start will get. Android's `loaderStopped`, the same rule. Only a
 * file played directly passes `idle`: a conversion's hls.js keeps loading
 * while paused, up to the minute, so it still waits for it.
 * @param {{ahead: number, idle: boolean}} at
 * @returns {boolean}
 */
export function loaderStopped(at) {
  const ahead = Number(at.ahead);
  return at.idle === true && Number.isFinite(ahead) && ahead > 0;
}
