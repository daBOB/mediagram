/**
 * Showing a person why a summary matched.
 *
 * An excerpt is a sentence lifted out of a document and shown on its own, so
 * it has two jobs the document did not: it has to contain the words that
 * earned the hit, and it has to read as prose rather than as markup.
 */

import { variants } from "./normalize";

/** Characters of summary either side of a match. */
const EXCERPT_PAD = 90;

/** A run of letters and digits — one word of the original text. */
const WORD = /[\p{Letter}\p{Number}]+/gu;

/**
 * Markdown markers out, because an excerpt is a sentence shown to a person.
 *
 * Deliberately crude: the window is cut mid-document, so any real parse would
 * be handed unbalanced markers anyway. Only the characters that read as noise
 * are dropped, and the words between them are left alone.
 */
function plain(text: string): string {
  return text
    .replace(/[*_`~]{1,3}/g, "")
    .replace(/^#{1,6}\s*/gm, "")
    .replace(/\s*#{1,6}\s+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Where in `summary` the first of `wanted` appears, or -1.
 *
 * Anchored on whole words of the original rather than on an offset into a
 * folded copy. The copy is not the original: umlauts spelled out run a
 * character longer, and runs of markup and blank lines collapse to a single
 * space, so an offset taken from it drifts further the more text precedes the
 * match — far enough, in a real summary, to slide the window clean past the
 * word it was supposed to show.
 *
 * Matching word by word sidesteps that entirely, and costs no reach: a term
 * is a run of letters and digits, because that is all `terms` can produce, so
 * a term that matches at all matches inside a single word. `variants` is the
 * same function the index matched with, so the two cannot disagree about a
 * hit and leave a result with no reason shown beside it.
 */
function anchor(summary: string, wanted: string[]): number {
  for (const match of summary.matchAll(WORD)) {
    const forms = variants(match[0]);
    if (wanted.some((term) => forms.some((form) => form.includes(term)))) return match.index;
  }
  return -1;
}

/**
 * The words around the first matching term, taken from the original text so
 * the reader sees their own language back rather than the folded copy.
 */
export function excerpt(summary: string | null, wanted: string[]): string | null {
  if (!summary) return null;

  const at = anchor(summary, wanted);
  if (at === -1) return null;

  const from = Math.max(0, at - EXCERPT_PAD);
  const to = Math.min(summary.length, at + EXCERPT_PAD);
  const body = plain(summary.slice(from, to));
  return `${from > 0 ? "…" : ""}${body}${to < summary.length ? "…" : ""}`;
}
