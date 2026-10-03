/**
 * The words and numbers on the Stats page, from the summary the server sends.
 *
 * Hand-rolled English and a 24-hour clock rather than `toLocale…`, for the
 * reason `format.js`'s `endsAt` gives: the page should read the same on every
 * host, and the Android app prints the same strings. Takes the clock rather
 * than reading it, so a test can say what "today" is.
 */

import { episodeLabel } from "../format.js";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_MS = 86_400_000;

/** What a history line calls each kind of entry. */
export const KIND_LABELS = { started: "Started", finished: "Finished", again: "Watched again" };

const two = (n) => String(n).padStart(2, "0");
const midnight = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();

/**
 * How long, in words: "under a minute", "42 min", "3 h 12 min", "3 h".
 * Minutes are floored — a bar never claims a minute nobody finished.
 * @param {number} seconds
 */
export function watchTime(seconds) {
  const minutes = Math.floor(seconds / 60);
  if (!(minutes >= 1)) return "under a minute";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/**
 * When, relative to `now`: "today 21:14", "Sat 21:14" within the last six
 * days, "21 Sep" before that, "21 Sep 2025" in another year. Calendar days on
 * this device's clock, so 00:10 is "today" and 23:50 last night is not.
 * @param {number} at epoch ms
 * @param {number} now epoch ms
 */
export function whenLabel(at, now) {
  const then = new Date(at);
  const today = new Date(now);
  // Rounded: a day with a daylight-saving change is 23 or 25 hours long.
  const daysAgo = Math.round((midnight(today) - midnight(then)) / DAY_MS);
  const clock = `${two(then.getHours())}:${two(then.getMinutes())}`;
  if (daysAgo === 0) return `today ${clock}`;
  if (daysAgo > 0 && daysAgo <= 6) return `${WEEKDAYS[then.getDay()]} ${clock}`;
  const date = `${then.getDate()} ${MONTHS[then.getMonth()]}`;
  return then.getFullYear() === today.getFullYear() ? date : `${date} ${then.getFullYear()}`;
}

/** "3 Oct", for a `YYYY-MM-DD` day. @param {string} day */
export function shortDate(day) {
  const [, month, date] = day.split("-").map(Number);
  return `${date} ${MONTHS[month - 1]}`;
}

/** "S" for Saturday — the letter under a day's bar. @param {string} day */
export function weekdayInitial(day) {
  return WEEKDAYS[new Date(`${day}T00:00:00Z`).getUTCDay()].slice(0, 1);
}

/**
 * The library's own name for a set, the way Continue watching names it: a
 * film by its title, an episode or lesson by its show and number. A set the
 * library no longer holds — or that this profile cannot see — is not named.
 */
export function historyTitle(set) {
  if (!set) return "No longer in the library";
  if (set.kind !== "movie" && set.show) return [set.show, episodeLabel(set)].filter(Boolean).join(" ");
  return set.title ?? set.setId;
}

/**
 * One history line: "Started · Der Pate · Sat 21:14 · 42 min". No duration
 * when nothing was counted — a finish from before stats existed was not
 * watched in under a minute.
 * @param {{ kind: "started"|"finished"|"again", at: number, seconds: number }} entry
 * @param {any} set the catalog set, or `null`
 * @param {number} now
 */
export function historyLine(entry, set, now) {
  const parts = [KIND_LABELS[entry.kind], historyTitle(set), whenLabel(entry.at, now)];
  if (entry.seconds > 0) parts.push(watchTime(entry.seconds));
  return parts.join(" · ");
}
