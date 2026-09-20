/**
 * Repeating a stretch of a title until it is understood.
 *
 * A lecture worth re-watching is a lecture worth looping, and the alternative
 * — seeking back by hand every thirty seconds — is the thing that makes people
 * give up on a difficult passage.
 *
 * Pure, and holding two numbers. The player owns the playhead; this owns only
 * the question of where the loop is and whether the playhead has left it.
 */

/**
 * The shortest loop worth having.
 *
 * Below this a loop is a stutter: the seek back lands inside its own gap and
 * the picture never settles. Also what stops a double-tap of `b` making a
 * zero-length loop that seeks for ever.
 */
const SHORTEST = 1;

/** Nothing set. The shape a loop has before anybody asks for one. */
export const NO_LOOP = { from: null, to: null };

/**
 * What `a` and `b` do, given where the playhead is.
 *
 * One key sets each end and `a` again clears, so the whole thing is reachable
 * without a menu and without a third key to mean "off".
 *
 * **`b` before `a` is not an error.** A viewer who marks the end of the
 * passage first has said something perfectly clear, so the two are sorted
 * rather than refused — refusing would be correct about the keystrokes and
 * wrong about the intent.
 */
export function markLoop(loop, end, at) {
  // `?? NaN` before the coercion. `Number(null)` is 0, and 0 is the start of
  // the film — without this, marking a loop from a playhead nothing knew
  // about would silently set its start to the first frame.
  const here = Number(at ?? Number.NaN);
  if (!Number.isFinite(here) || here < 0) return loop;

  if (end === "from") {
    // `a` twice means "no loop after all", which is the only way back out.
    if (loop.from !== null) return { ...NO_LOOP };
    return tidy(here, loop.to);
  }
  if (end === "to") return tidy(loop.from, here);
  return loop;
}

/** Whether both ends are set and far enough apart to be worth playing. */
export function isLooping(loop) {
  return loop.from !== null && loop.to !== null && loop.to - loop.from >= SHORTEST;
}

/**
 * Where the playhead should go, or `null` to leave it alone.
 *
 * Called on `timeupdate`, which fires about four times a second — so this
 * answers `null` for all but one of them, and says so cheaply.
 *
 * Also catches a playhead *before* the loop, which happens when a viewer
 * seeks backwards out of it. Sending them forward to the start is what the
 * loop means: they asked for this passage, not for whatever precedes it.
 */
export function loopBack(loop, at) {
  if (!isLooping(loop)) return null;
  const here = Number(at ?? Number.NaN);
  if (!Number.isFinite(here)) return null;
  return here >= loop.to || here < loop.from ? loop.from : null;
}

/** `1:30 – 2:45`, or half of it while a viewer is still marking it out. */
export function loopLabel(loop, clock) {
  if (loop.from === null && loop.to === null) return "";
  const from = loop.from === null ? "…" : clock(loop.from);
  const to = loop.to === null ? "…" : clock(loop.to);
  return `${from} – ${to}`;
}

/** Both ends in the order a loop needs them, whichever order they arrived in. */
function tidy(from, to) {
  if (from === null || to === null) return { from, to };
  return from <= to ? { from, to } : { from: to, to: from };
}
