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
