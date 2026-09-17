/**
 * Naming a subtitle track in the menu a viewer actually reads.
 *
 * The catalog carries language codes, because that is what the files are
 * tagged with. A menu showing "und" tells a viewer nothing, and "de" barely
 * more, so the code is turned into a name on the way to the screen.
 *
 * The names come from the platform rather than a table kept here: a table
 * would cover the handful of languages this library happens to hold today and
 * be wrong about the next one, and every browser already ships the full set.
 */

/**
 * What a track tagged `und` is called. The tag is the standard's way of
 * saying "undetermined", which is exactly what a file with no language
 * metadata deserves — and exactly what a viewer should not be shown.
 */
const UNTAGGED = "Subtitles";

const NAMES = new Intl.DisplayNames(["en"], { type: "language" });

/** The name to show for a subtitle track tagged `lang`. */
export function subtitleLabel(lang) {
  if (!lang || lang === "und") return UNTAGGED;
  try {
    // Returns the code unchanged when it names no language it knows, which is
    // the right answer: better an unfamiliar code than a confident wrong name.
    return NAMES.of(lang) ?? lang;
  } catch {
    // A structurally invalid tag throws. A label is not worth a broken menu.
    return lang;
  }
}
