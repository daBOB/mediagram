/** How an episode is named on screen; Android's `EpisodeLabel.kt` is the same rule. */

const isCount = (n) => Number.isInteger(n) && n >= 0;

/**
 * The episode number as a viewer reads it: `4`, or `4-5` for a file holding
 * more than one. Empty when unnumbered or unreadable.
 *
 * The index stores the uploader's JSON (`4` or `[4,5]`), and the Android core
 * parses it the same way, dropping what is not a whole number or a pair of
 * them; so a stored `04` or `4-5` reads as unnumbered on both surfaces.
 */
export function episodeNumber(set) {
  let parsed;
  try {
    parsed = JSON.parse(set.episode ?? "null");
  } catch {
    return "";
  }
  if (isCount(parsed)) return String(parsed);
  if (Array.isArray(parsed) && parsed.length === 2 && parsed.every(isCount)) {
    return parsed[0] === parsed[1] ? String(parsed[0]) : `${parsed[0]}-${parsed[1]}`;
  }
  return "";
}

/** `S1E4` for an episode, `4` for a lesson, `S1E4-5` for a range, empty when unnumbered. */
export function episodeLabel(set) {
  const number = episodeNumber(set);
  if (!number) return "";
  return set.kind === "ep" && set.season != null ? `S${set.season}E${number}` : number;
}
