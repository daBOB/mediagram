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
 * **`starved` is what decides whether this says "buffering", not
 * `readyState`.** The two are not the same thing and confusing them was a
 * bug: `readyState` is a *level of readiness*, and its second-highest level,
 * `HAVE_FUTURE_DATA`, means "I can play forward" — the ordinary, healthy
 * state of a video playing from a buffer that is deliberately capped. Every
 * transcoded title is fed by hls.js, which caps its buffer on purpose, so
 * reading anything below `HAVE_ENOUGH_DATA` as "buffering" labelled a film
 * that was entirely on local disk, fully encoded and playing perfectly, as
 * though it were waiting on the network for its whole running time.
 *
 * Buffering is a thing that is *happening*, so it is taken from the element
 * saying so: `waiting` and `stalled` mean starved, `playing` and `canplay`
 * mean it is not. `readyState` is still what separates a player that has no
 * frame yet from one that has.
 *
 * Described rather than positional because there are five of these now, and
 * `preloadReadout(3, 12, null, 0, false)` says nothing at its call site.
 *
 * `awaitingStart` is a title that started itself and is holding on until it
 * has enough in hand. It is the state that would otherwise read "ready" while
 * nothing happened, which looks broken rather than deliberate.
 *
 * @param {{readyState: number, ahead: number, starved?: boolean,
 *          awaitingStart?: boolean, fillRate?: number|null,
 *          dropped?: number}} at
 */
export function preloadReadout(at = {}) {
  const ready = Number(at.readyState) || 0;
  const aheadSeconds = Number(at.ahead);
  const ahead = Number.isFinite(aheadSeconds) && aheadSeconds > 0 ? aheadSeconds : 0;

  // Nothing to show yet is its own state: not starved, just not started.
  const state =
    at.awaitingStart === true
      ? "getting ready"
      : ready < 1
        ? "opening"
        : at.starved === true
          ? "buffering"
          : "ready";
  const parts = [ahead > 0 ? `${state} · ${clockTime(ahead)} ahead` : state];

  // `Number(null)` is 0, and 0 is a rate worth showing — it is a dead stall.
  // So an absent measurement is separated from a measured zero here rather
  // than left to coercion, which cannot tell them apart.
  const rate = at.fillRate === null || at.fillRate === undefined ? Number.NaN : Number(at.fillRate);
  if (Number.isFinite(rate) && rate >= 0 && Math.abs(rate - 1) > UNREMARKABLE) {
    parts.push(`filling ${rate.toFixed(1)}×`);
  }

  // Only when there are some. A zero is the ordinary case, and a readout that
  // reports the ordinary case has spent a viewer's attention on nothing.
  const dropped = Number(at.dropped);
  if (Number.isFinite(dropped) && dropped > 0) {
    parts.push(`${dropped} dropped`);
  }

  return parts.join(", ");
}
