/**
 * Turning index values into something a viewer reads at a glance.
 *
 * Loaded by the browser and by the tests, so there is one copy.
 */

const UNITS = ["B", "KB", "MB", "GB", "TB"];

/** A byte count, at one decimal place only where that changes the meaning. */
export function humanSize(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return "";
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit += 1;
  }
  const rounded = value < 10 && unit > 0 ? value.toFixed(1) : String(Math.round(value));
  return `${rounded} ${UNITS[unit]}`;
}

/** A duration in seconds as hours and minutes; a film is "2h 2m". */
export function humanDuration(seconds) {
  if (!seconds || seconds < 0) return "";
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.round((seconds % 3600) / 60);
  if (hours === 0) return `${Math.max(1, minutes)}m`;
  return minutes === 0 ? `${hours}h` : `${hours}h ${minutes}m`;
}

/** `S1E4` for an episode, `4` for a lesson, empty when unnumbered. */
export function episodeLabel(set) {
  if (!set.episode) return "";
  if (set.kind === "ep" && set.season != null) return `S${set.season}E${set.episode}`;
  return String(set.episode);
}

/** The technical line under a title: what it is, not where it came from. */
export function codecLine(set) {
  return [set.container, set.vcodec, set.acodec].filter(Boolean).join(" · ");
}

/**
 * A position on a scrub bar: `1:23`, or `1:23:45` once past an hour.
 *
 * Distinct from `humanDuration`, which rounds to whole minutes and is right
 * for a card but useless as a clock: a running position that says "2h 2m"
 * never appears to move.
 */
export function clockTime(seconds) {
  const total = Number.isFinite(seconds) && seconds > 0 ? Math.floor(seconds) : 0;
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const rest = String(total % 60).padStart(2, "0");
  if (hours === 0) return `${minutes}:${rest}`;
  return `${hours}:${String(minutes).padStart(2, "0")}:${rest}`;
}

/**
 * Numbers a catalogue prints as words.
 *
 * Stops at twenty because that is roughly where a spelled-out number stops
 * being quicker to read than the figure: "seventeen lessons" reads, "one
 * hundred and seventy lessons" is just long.
 */
const NUMBER_WORDS = [
  "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
  "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
  "nineteen", "twenty",
];

/** A count as a word while it is small enough to be one, else as figures. */
export function spellCount(count) {
  if (!Number.isInteger(count) || count < 0) return "";
  return count <= 20 ? NUMBER_WORDS[count] : String(count);
}

/** An extent: `three shows`, `one show`, `170 lessons`. */
export function countOf(count, noun) {
  return `${spellCount(count)} ${noun}${count === 1 ? "" : "s"}`;
}

/**
 * The wall-clock time something with `remainingSeconds` left will finish.
 *
 * Takes the clock rather than reading it, so a test can assert on the answer
 * and so a paused film can be asked the same question repeatedly and get a
 * later answer each time — which is the truth about a paused film.
 *
 * Hand-rolled 24-hour rather than `toLocaleTimeString`, for the same reason
 * `clockTime` is: the format should not change with the host's locale when it
 * is sitting in a row of tabular figures that do not.
 */
export function endsAt(remainingSeconds, now) {
  if (!Number.isFinite(remainingSeconds) || remainingSeconds < 0) return "";
  // `Date` carries the rollover, so a film finishing after midnight says
  // 00:20 rather than 24:20.
  const end = new Date(now.getTime() + remainingSeconds * 1000);
  return `${String(end.getHours()).padStart(2, "0")}:${String(end.getMinutes()).padStart(2, "0")}`;
}
