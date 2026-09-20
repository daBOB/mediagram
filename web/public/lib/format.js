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
 * The average bitrate of a set, as `9.4 Mbps`.
 *
 * `total / duration` over the whole file, which is what a link has to carry
 * on average rather than what any one second of it peaks at. Empty unless
 * both numbers are there and positive: a row missing either would otherwise
 * read `NaN Mbps`.
 */
export function bitrateLabel(set) {
  const total = Number(set.total);
  const duration = Number(set.duration);
  if (!Number.isFinite(total) || !Number.isFinite(duration)) return "";
  if (total <= 0 || duration <= 0) return "";

  const mbps = (total * 8) / duration / 1e6;
  // Under 10 Mbit/s the first decimal is the difference between a link that
  // carries it and one that does not; above, it is a digit nobody reads.
  return `${mbps < 10 ? mbps.toFixed(1) : String(Math.round(mbps))} Mbps`;
}

/**
 * Everything the index knows about a file, for the places with room to say it.
 *
 * Longer than `codecLine`, which stays as it is for a search hit and a lesson
 * row — both are one line in a list and a fuller string would wrap.
 *
 *
 * The bitrate is last because it is the one that makes the `needs transcode`
 * badge legible: a viewer who sees 13.9 Mbps beside the badge understands
 * it, and a viewer who sees only the badge does not.
 */
/**
 * What a title's dynamic range is worth saying, or nothing.
 *
 * `SDR` is left out on purpose. It is the absence of a fact rather than a
 * fact, and a shelf where every card says `SDR` says nothing at all; the
 * three that are worth naming are `HDR10`, `HLG` and `DV`.
 */
export function hdrLabel(set) {
  return set.hdr && set.hdr !== "SDR" ? set.hdr : null;
}

export function technicalLine(set) {
  const hdr = hdrLabel(set);
  return [
    set.quality,
    hdr,
    set.container,
    set.vcodec,
    set.acodec,
    // `humanSize(0)` is "0 B", which is a fact about nothing. Playable sets
    // always have a size; a row that somehow does not should stay quiet.
    Number(set.total) > 0 ? humanSize(set.total) : "",
    partsLabel(set.partCount),
    bitrateLabel(set),
  ]
    .filter(Boolean)
    .join(" · ");
}

/**
 * `5 parts`, or nothing for a set that is a single message.
 *
 * A one-part set is the ordinary case and saying so is noise; a set split
 * into several is the reason a download can stall halfway through one.
 */
function partsLabel(count) {
  const parts = Number(count);
  return Number.isFinite(parts) && parts > 1 ? `${parts} parts` : "";
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
