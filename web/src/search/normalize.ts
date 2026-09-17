/**
 * Folding text so a search finds what a person meant.
 *
 * This library is German. Someone hunting "Überblick" types `uberblick` as
 * often as not, and a search that answers "no results" to that is a search
 * nobody uses twice. So both the query and the text being searched are folded
 * the same way, and matching happens on the folded forms.
 *
 * The titles here also carry characters no keyboard offers — a fraction slash
 * in "Produkte ⁄ Instrumente", an en dash in "Zeitebenen – Teil 1". Those
 * collapse to spaces rather than being deleted, so the words either side stay
 * separate words.
 */

/**
 * `ß` has no decomposed form, so it survives the diacritic strip below and
 * has to be spelled out. People without it on their keyboard write `ss`.
 */
const SPELLED_OUT: Array<[RegExp, string]> = [
  [/ß/g, "ss"],
  [/æ/g, "ae"],
  [/œ/g, "oe"],
  [/ø/g, "o"],
  [/đ|ð/g, "d"],
  [/ł/g, "l"],
  [/þ/g, "th"],
];

/**
 * The folded form of `text`: lower case, no diacritics, single-spaced, and
 * with anything that is not a letter or a digit turned into a space.
 */
export function fold(text: string | null | undefined): string {
  if (!text) return "";

  let folded = text.toLowerCase();
  for (const [pattern, replacement] of SPELLED_OUT) folded = folded.replace(pattern, replacement);

  return folded
    // NFD splits "ü" into "u" + combining diaeresis, which the next step drops.
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[^\p{Letter}\p{Number}]+/gu, " ")
    .trim();
}

/** The folded words of a query. An empty query yields no terms. */
export function terms(query: string | null | undefined): string[] {
  const folded = fold(query);
  return folded === "" ? [] : folded.split(" ");
}
