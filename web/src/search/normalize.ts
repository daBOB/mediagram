/**
 * Folding text so a search finds what a person meant.
 *
 * This library is German, and a German word written on a keyboard without
 * umlauts comes out one of two ways: the dots are dropped (`uberblick`) or
 * they are spelled out (`ueberblick`). Both are in daily use, so the text
 * being searched is kept in both spellings and a query is matched against
 * either. A search that answers "no results" to one of them is a search
 * nobody uses twice.
 *
 * The titles here also carry characters no keyboard offers — a fraction slash
 * in "Produkte ⁄ Instrumente", an en dash in "Zeitebenen – Teil 1". Those
 * collapse to spaces rather than being deleted, so the words either side stay
 * separate words.
 */

/**
 * Letters with no decomposed form, which therefore survive the diacritic
 * strip below and have to be written out. `ß` is the one that matters here;
 * the rest keep the fold honest for a library that is not only German.
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
 * The three umlauts, in the two-letter spelling German uses for them. Only
 * these three: "é" has no such convention, and nobody types "cafee".
 */
const UMLAUTS: Array<[RegExp, string]> = [
  [/ä/g, "ae"],
  [/ö/g, "oe"],
  [/ü/g, "ue"],
];

/** Lower case, no diacritics, single-spaced, nothing but letters and digits. */
function flatten(text: string): string {
  return (
    text
      // NFD splits "ü" into "u" + combining diaeresis, which the next step drops.
      .normalize("NFD")
      .replace(/\p{Diacritic}/gu, "")
      .replace(/[^\p{Letter}\p{Number}]+/gu, " ")
      .trim()
  );
}

/**
 * The folded form of `text`, with diacritics dropped: "Überblick" becomes
 * `uberblick`.
 *
 * This is the form a query is folded into, because a query that already
 * spells an umlaut out carries no diacritic for this to drop — `ueberblick`
 * passes through unchanged and meets the text's spelled-out copy instead.
 */
export function fold(text: string | null | undefined): string {
  if (!text) return "";

  let folded = text.toLowerCase();
  for (const [pattern, replacement] of SPELLED_OUT) folded = folded.replace(pattern, replacement);

  return flatten(folded);
}

/**
 * The folded form of `text` with umlauts spelled out instead of dropped:
 * "Überblick" becomes `ueberblick`.
 *
 * Composed first, because text that arrives already decomposed carries its
 * umlaut as two code points and would otherwise slip past the map and get
 * stripped by `flatten` — silently turning this back into `fold`.
 */
export function spellOut(text: string | null | undefined): string {
  if (!text) return "";

  let spelled = text.toLowerCase().normalize("NFC");
  for (const [pattern, replacement] of UMLAUTS) spelled = spelled.replace(pattern, replacement);
  for (const [pattern, replacement] of SPELLED_OUT) spelled = spelled.replace(pattern, replacement);

  return flatten(spelled);
}

/** The folded words of a query. An empty query yields no terms. */
export function terms(query: string | null | undefined): string[] {
  const folded = fold(query);
  return folded === "" ? [] : folded.split(" ");
}
