/**
 * Whether a recorded position is somewhere to go back to.
 *
 * Not every position is. A title stopped twenty seconds in was not being
 * watched, it was being looked at; one stopped in the closing credits is
 * finished, and offering to resume it means offering the credits. Both are
 * worse than starting from the beginning, and both happen constantly.
 *
 * Pure, and kept apart from the store, because these are judgements about
 * what a number means rather than bookkeeping about where it lives.
 */

/** Before this, a title was opened rather than watched. */
const TOO_EARLY_SECONDS = 30;

/** Or this share of a title too short for half a minute to be a glance. */
const TOO_EARLY_FRACTION = 0.1;

/** What is left at the end of a title is the credits. */
const CREDITS_FRACTION = 0.05;

/** Never more than this, or the last twelve minutes of a film would count. */
const CREDITS_SECONDS = 60;

/**
 * The tail of a title that counts as having finished it.
 *
 * The **smaller** of the two, which is the whole subtlety here. A share alone
 * is wrong for a long film: five percent of four hours is twelve minutes, and
 * nobody has finished a film with twelve minutes left. A fixed minute alone is
 * wrong for everything short: this library holds three titles under a minute
 * and eighty-four under five, and a one-minute tail makes the first of those
 * finished before they have started and the rest finished seconds in.
 *
 * Taking the smaller gives the last minute of anything over twenty minutes,
 * and a proportionate sliver of anything below — which is the rule the tests
 * described long before the code did it.
 */
function creditsOf(runtime) {
  return Math.min(runtime * CREDITS_FRACTION, CREDITS_SECONDS);
}

/**
 * Whether `at` is the end of a title `runtime` long.
 *
 * Unknowable without a runtime, and answered `false` there: a title that may
 * or may not be finished is better offered than silently dropped from the
 * shelf that exists to remind you of it.
 */
export function isFinished(at, runtime) {
  if (!Number.isFinite(runtime) || runtime <= 0) return false;
  if (!Number.isFinite(at) || at < 0) return false;
  return runtime - at <= creditsOf(runtime);
}

/**
 * Where to start `setId` given what was recorded, or `null` for the top.
 *
 * `null` covers all three ways a position is not a resume point: there is
 * none, it is too near the start to be worth keeping, or the title is done.
 */
export function resumeAt(progress) {
  if (!progress) return null;
  const at = Number(progress.at);
  if (!Number.isFinite(at)) return null;

  const runtime = Number(progress.duration);
  // Scaled for the same reason the tail is: half a minute is a glance at a
  // film and most of a two-minute lesson. With no runtime the flat half
  // minute is all there is to go on.
  const glance =
    Number.isFinite(runtime) && runtime > 0
      ? Math.min(TOO_EARLY_SECONDS, runtime * TOO_EARLY_FRACTION)
      : TOO_EARLY_SECONDS;
  if (at < glance) return null;

  if (isFinished(at, runtime)) return null;
  return at;
}

/**
 * How far through, 0 to 1, or `null` when the runtime is unknown.
 *
 * `null` rather than a guess: a progress rule drawn from an unknown length is
 * a bar that means nothing, and a bar that means nothing is worse than none.
 */
export function watchedFraction(progress) {
  if (!progress) return null;
  const runtime = Number(progress.duration);
  const at = Number(progress.at);
  if (!Number.isFinite(runtime) || runtime <= 0 || !Number.isFinite(at)) return null;
  return Math.min(1, Math.max(0, at / runtime));
}

/**
 * Which runtime may be believed, of the two that might be known.
 *
 * The catalog's is the file's real length, measured by the uploader, and wins
 * whenever it is there. The browser's is only worth having when the browser
 * has the whole file — which it does when playing directly, and does not when
 * watching a conversion: `video.duration` is then the length of what has been
 * encoded so far and grows as it goes.
 *
 * Believing that would put the end of the film a few seconds ahead of the
 * viewer, for the whole film. Everything downstream reads that as finished:
 * the position is deleted on every save, the title never reaches the Continue
 * shelf, and nothing about it looks wrong. Answering 0 instead means "nobody
 * knows", and `isFinished` and `watchedFraction` both refuse to judge without
 * one — a title that keeps its place is a far better failure.
 */
export function trustedRuntime({ catalogued, observed, direct }) {
  const known = Number(catalogued);
  if (Number.isFinite(known) && known > 0) return known;
  const seen = Number(observed);
  return direct && Number.isFinite(seen) && seen > 0 ? seen : 0;
}
