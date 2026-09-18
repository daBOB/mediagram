/**
 * What the player is doing before, and while, it plays.
 *
 * The player preloads without starting, which means a viewer can be looking
 * at a still first frame for several seconds with nothing to say whether
 * anything is happening. This is that something: how far ahead the buffer
 * reaches, and whether the browser believes it could play the title through.
 *
 * Both halves are pure so they can be tested without a media element, which
 * is the only practical way to assert on `readyState` and `TimeRanges`.
 */

import { clockTime } from "./format.js";

/**
 * Seconds of video ready beyond `currentTime`.
 *
 * Takes the range that actually contains the playhead rather than the last
 * one. They are usually the same; they are not after a seek, when the buffer
 * holds a stretch from where the viewer was as well as where they now are,
 * and the last range would report a buffer that is nowhere near them.
 */
export function bufferedAhead(buffered, currentTime) {
  if (!buffered || typeof buffered.length !== "number") return 0;
  for (let i = 0; i < buffered.length; i++) {
    const start = buffered.start(i);
    const end = buffered.end(i);
    // A hair of tolerance at the start: a playhead sitting a few milliseconds
    // before a range it plainly belongs to would otherwise report nothing.
    if (currentTime >= start - 0.5 && currentTime <= end) {
      return Math.max(0, end - currentTime);
    }
  }
  return 0;
}

/**
 * The readout itself.
 *
 * `readyState` is the browser's own verdict and worth repeating because it is
 * the one thing the buffer length cannot say: four seconds buffered means
 * something different on a thirty-second clip and on a two-hour film, and
 * `HAVE_ENOUGH_DATA` is the browser saying it has done that arithmetic.
 */
export function preloadReadout(readyState, aheadSeconds) {
  const ready = Number(readyState) || 0;
  const ahead = Number.isFinite(aheadSeconds) && aheadSeconds > 0 ? aheadSeconds : 0;

  // 4 is HAVE_ENOUGH_DATA: it could play to the end without stopping.
  const state = ready >= 4 ? "ready" : ready >= 1 ? "buffering" : "opening";
  return ahead > 0 ? `${state} · ${clockTime(ahead)} ahead` : state;
}
