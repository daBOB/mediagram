/**
 * How much of a position write counts as watching.
 *
 * Watch time is the wall-clock time between two consecutive position writes
 * of the same title, counted only when the position moved forward, and never
 * more than either the distance moved or `STEP_CAP_SECONDS`. That one rule
 * keeps a pause, a seek and a sleeping laptop out of the total without the
 * engine having to know which of them happened: a seek forward counts the
 * wall time spent, a pause counts only the time since playing resumed, 2×
 * speed counts wall time, and a seek back counts nothing.
 *
 * Known ceiling: a throttled background tab that saves once a minute counts
 * 15 s per minute.
 *
 * Pure, and pinned by `test/fixtures/watch-state/stats-step.json`, which the
 * Android core runs too.
 */

/** 1.5 × the player's 10 s save tick: one late tick still counts in full. */
export const STEP_CAP_SECONDS = 15;

/** A title's previous position write in this process: where, and when. */
export interface LastTick {
  /** The position, in seconds. */
  at: number;
  /** The wall clock at that write, in epoch ms. */
  wallMs: number;
}

/** Seconds of watching between `prev` and a write at `at`, made at `nowMs`. */
export function stepSeconds(prev: LastTick | null | undefined, at: number, nowMs: number): number {
  if (!prev) return 0;
  const dPos = at - prev.at;
  const dWall = (nowMs - prev.wallMs) / 1000;
  if (!(dPos > 0) || !(dWall > 0)) return 0;
  return Math.min(dPos, dWall, STEP_CAP_SECONDS);
}

/**
 * Whether this write starts a finished title over: there was no position
 * for it, yet it is marked watched. A title underway since its restart —
 * which has a position again — is not started over a second time.
 */
export function againNow(hadProgress: boolean, watchedLive: boolean): boolean {
  return !hadProgress && watchedLive;
}
