/**
 * Finding a title among a hundred and seventy.
 *
 * Ranking is the whole feature. A course of lessons called "Interpretation",
 * "Definition" and "Mobile App" answers almost any query with a dozen hits,
 * so the order they arrive in decides whether the search was any use. A title
 * match beats the folder the lesson sits in, which beats something buried in
 * a summary.
 *
 * Everything is folded once, at construction, and matched as plain substrings
 * of the folded text. At this size that is both simpler and faster than an
 * index: the catalog is read-only for the life of the process, so the folded
 * copy can be built at startup and never invalidated. A library of thousands
 * would want SQLite's FTS5 instead, and would need the schema to carry it.
 */

import { excerpt } from "./excerpt";
import { terms, variants } from "./normalize";

/** A set as the search sees it: its text, and nothing else. */
export interface Searchable {
  setId: string;
  kind: string;
  title: string | null;
  show: string | null;
  chap: string | null;
  path: string | null;
  summary: string | null;
}

/** Which field earned a hit. Ordered: earlier is a stronger match. */
export const FIELDS = ["title", "show", "chap", "path", "summary"] as const;
export type Field = (typeof FIELDS)[number];

/**
 * Why a set matched. Carried alongside whatever the caller put in, rather
 * than replacing it: the router hands in full catalog rows and needs them
 * back whole, because a hit is opened by the same dialog a shelf row is.
 */
export interface Why {
  /** The strongest field any term matched on. */
  matched: Field;
  /** The words around a summary match, or `null` when the hit speaks for itself. */
  excerpt: string | null;
}

export type Hit<T extends Searchable = Searchable> = T & Why;

/** More than a screenful is not a result list, it is the library again. */
const MAX_HITS = 50;

interface Entry<T> {
  set: T;
  /**
   * Each field's searchable forms, in `FIELDS` order: how it reads folded,
   * and how it reads with the umlauts spelled out when that differs.
   *
   * Both spellings are kept on the *text* side rather than collapsing `ue`
   * back to `u` on the query side, which would be cheaper and wrong: it would
   * make "steuer" match "Fenster". Summaries are the bulk of it — a second
   * copy of every German summary, which is megabytes rather than nothing.
   */
  forms: string[][];
}

export class SearchIndex<T extends Searchable = Searchable> {
  private readonly entries: Entry<T>[];

  constructor(sets: T[]) {
    this.entries = sets.map((set) => ({
      set,
      forms: FIELDS.map((field) => variants(set[field])),
    }));
  }

  /**
   * The sets matching every term of `query`, best first.
   *
   * Every term has to match *somewhere* on a set, but not all in the same
   * field: "signal interpretation" should find the lesson called
   * Interpretation that sits in the Signal chapter.
   */
  search(query: string): Hit<T>[] {
    const wanted = terms(query);
    if (wanted.length === 0) return [];

    const hits: Array<{ set: T; rank: number; field: Field }> = [];
    for (const entry of this.entries) {
      // The strongest field any term matched, as an index into FIELDS.
      let best: number = FIELDS.length;
      let matchedAll = true;

      for (const term of wanted) {
        const at = entry.forms.findIndex((forms) => forms.some((text) => text.includes(term)));
        if (at === -1) {
          matchedAll = false;
          break;
        }
        best = Math.min(best, at);
      }
      if (!matchedAll) continue;

      const field = FIELDS[best];
      // Unreachable: `best` only moves down from FIELDS.length when a term
      // matched, and a set with no match was skipped above.
      if (field === undefined) continue;
      hits.push({ set: entry.set, rank: best, field });
    }

    // Field first, then title, so repeated runs of the same query agree.
    hits.sort((a, b) => a.rank - b.rank || (a.set.title ?? "").localeCompare(b.set.title ?? "", "de"));

    // Cut to what is returned before building anything, because an excerpt
    // costs a walk of the whole summary and a common word matches every
    // lesson in the course. Ranking needs only the field, which is already
    // known, so nothing here changes the order.
    return hits.slice(0, MAX_HITS).map(({ set, field }) => ({
      ...set,
      matched: field,
      excerpt: field === "summary" ? excerpt(set.summary, wanted) : null,
    }));
  }
}
