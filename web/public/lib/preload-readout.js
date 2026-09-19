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
 * How close to 1.0 counts as simply keeping up.
 *
 * Inside this band the rate is the uninteresting answer, and a readout that
 * flickers between "1.0×" and "1.1×" every second is worse than one that
 * says nothing: it draws the eye to a number that is not telling anyone
 * anything.
 */
const UNREMARKABLE = 0.15;

/**
 * The readout itself.
 *
 * `readyState` is the browser's own verdict and worth repeating because it is
 * the one thing the buffer length cannot say: four seconds buffered means
 * something different on a thirty-second clip and on a two-hour film, and
 * `HAVE_ENOUGH_DATA` is the browser saying it has done that arithmetic.
 *
 * `fillRate` is the third fact and the one the other two cannot give. Depth
 * alone cannot separate a player that has all it wants from one that is
 * falling behind — both stop growing — and it is the rate that says which.
 * Optional because it is unknowable more often than not: see `fillRate()` in
 * `adapt-playback.js`.
 *
 * @param {number} readyState the element's own `readyState`
 * @param {number} aheadSeconds seconds buffered beyond the playhead
 * @param {number|null} [fillRate] buffered seconds gained per second of clock
 * @param {number} [droppedFrames] frames the decoder gave up on
 */
export function preloadReadout(readyState, aheadSeconds, fillRate, droppedFrames) {
  const ready = Number(readyState) || 0;
  const ahead = Number.isFinite(aheadSeconds) && aheadSeconds > 0 ? aheadSeconds : 0;

  // 4 is HAVE_ENOUGH_DATA: it could play to the end without stopping.
  const state = ready >= 4 ? "ready" : ready >= 1 ? "buffering" : "opening";
  const parts = [ahead > 0 ? `${state} · ${clockTime(ahead)} ahead` : state];

  // `Number(null)` is 0, and 0 is a rate worth showing — it is a dead stall.
  // So an absent measurement is separated from a measured zero here rather
  // than left to coercion, which cannot tell them apart.
  const rate = fillRate === null || fillRate === undefined ? Number.NaN : Number(fillRate);
  if (Number.isFinite(rate) && rate >= 0 && Math.abs(rate - 1) > UNREMARKABLE) {
    parts.push(`filling ${rate.toFixed(1)}×`);
  }

  // Only when there are some. A zero is the ordinary case, and a readout that
  // reports the ordinary case has spent a viewer's attention on nothing.
  const dropped = Number(droppedFrames);
  if (Number.isFinite(dropped) && dropped > 0) {
    parts.push(`${dropped} dropped`);
  }

  return parts.join(", ");
}
