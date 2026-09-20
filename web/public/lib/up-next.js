/**
 * When the next title is offered, and when it starts on its own.
 *
 * These were one decision and should have been two. Offering the next episode
 * a little before the end is right: it is a heads-up, and it puts a way past
 * the credits within reach. Starting it before the end is not, and that is
 * what happened — the panel appeared thirty seconds out and carried a ten
 * second timer, so every episode was cut off with twenty seconds still to
 * play.
 *
 * So the phase is worked out here, from facts rather than from whether a
 * timer happens to be running, and counting cannot begin before the title has
 * actually ended.
 */

/** How long before the end the next title is offered. */
export const WARN_SECONDS = 30;

/** How long the viewer has, once it has ended, before it goes on its own. */
export const COUNTDOWN_SECONDS = 10;

/**
 * @param {{hasNext: boolean, cancelled: boolean,
 *          remainingSeconds: number|null, ended: boolean}} at
 * @returns {"hidden"|"waiting"|"counting"}
 *
 * `waiting` shows the panel and says what follows; `counting` is the only
 * phase that may start it.
 */
export function upNextPhase(at) {
  if (!at.hasNext || at.cancelled) return "hidden";
  // The end is the end however it arrived — run out, or seeked past the last
  // frame. Either way there is nothing left of this title to interrupt.
  if (at.ended) return "counting";

  // `Number(null)` is 0, and 0 seconds left is the one value that would open
  // the panel immediately — so an unknown runtime is separated from a real
  // zero here rather than left to coercion, which cannot tell them apart.
  const given = at.remainingSeconds;
  const left = given === null || given === undefined ? Number.NaN : Number(given);
  // An unknown runtime cannot be counted down from. Nothing is offered early
  // rather than something being offered at the wrong moment; `ended` still
  // catches it.
  if (!Number.isFinite(left)) return "hidden";
  return left <= WARN_SECONDS ? "waiting" : "hidden";
}
