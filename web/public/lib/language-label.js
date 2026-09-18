/**
 * Naming a track in a menu a viewer actually reads.
 *
 * The catalog and the file both carry language codes, because that is what
 * the tags are. A menu row reading "und" tells a viewer nothing, and "de"
 * barely more, so the code is turned into a name on the way to the screen.
 *
 * The names come from the platform rather than a table kept here: a table
 * would cover the handful of languages this library happens to hold today and
 * be wrong about the next one, and every browser already ships the full set.
 *
 * Used for subtitles and for audio, which is why the fallback is the caller's
 * to give: an untagged subtitle track is "Subtitles", an untagged audio track
 * is the track's number, and neither is a good name for the other.
 */

const NAMES = new Intl.DisplayNames(["en"], { type: "language" });

/**
 * The name to show for a track tagged `lang`, or `fallback` when it is tagged
 * with nothing useful.
 *
 * `und` counts as nothing useful: it is the standard's way of saying
 * "undetermined", which is exactly what a file with no language metadata
 * deserves — and exactly what a viewer should not be shown.
 */
export function languageLabel(lang, fallback) {
  if (!lang || lang === "und") return fallback;
  try {
    // Returns the code unchanged when it names no language it knows, which is
    // the right answer: better an unfamiliar code than a confident wrong name.
    return NAMES.of(lang) ?? lang;
  } catch {
    // A structurally invalid tag throws. A label is not worth a broken menu.
    return lang;
  }
}
