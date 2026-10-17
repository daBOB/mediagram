/**
 * A kid's daily watching limit: the rule, its words and its choices.
 *
 * Pure, and the one place they are written for the page; the Android app
 * holds its port to the same `daily-limit.json` fixture. The count itself —
 * seconds watched today — is the server's (`GET /api/profiles/{id}/today`);
 * nothing here adds up time.
 */

/** At or under this much left, the kid is told once. */
export const WARN_SECONDS = 300;

export const DAILY_LIMIT_WORDS = {
  label: "Daily limit",
  warning: "Only a few minutes of watching left today. This one can finish.",
  out: "That's all the watching for today. See you tomorrow.",
};

/** What Manage profiles offers, in order; `null` is no limit. */
export const DAILY_LIMIT_CHOICES = [
  { minutes: null, label: "No limit" },
  { minutes: 30, label: "30 min" },
  { minutes: 45, label: "45 min" },
  { minutes: 60, label: "1 h" },
  { minutes: 90, label: "1 h 30 min" },
  { minutes: 120, label: "2 h" },
  { minutes: 180, label: "3 h" },
];

/**
 * `free` (no limit), `ok`, `warn` (under five minutes left, up to the last
 * second) or `out`; `leftSeconds` never goes below 0, and is `null` when free.
 */
export function dailyLimitState(limitMinutes, watchedSeconds) {
  if (limitMinutes === null || limitMinutes === undefined) return { state: "free", leftSeconds: null };
  const leftSeconds = Math.max(0, limitMinutes * 60 - watchedSeconds);
  const state = leftSeconds <= 0 ? "out" : leftSeconds <= WARN_SECONDS ? "warn" : "ok";
  return { state, leftSeconds };
}
