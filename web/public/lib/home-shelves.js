/**
 * What the start page shows, decided before anything is drawn.
 *
 * A library of six hundred titles has two facts about it worth landing on:
 * what arrived recently, and what was already underway. Everything here
 * derives those two from the catalog and the viewer's own state, and nothing
 * here touches the DOM — the rules are the part that can be got wrong, so
 * they are the part that is tested.
 *
 * The rule with any real substance is what a show's *next* episode is, and
 * it is written out in `nextInCollection`.
 */

import { flattenCollection, nextAfter } from "./library.js";
import { resumeAt } from "./resume-point.js";

/** How many cards a row holds before the rest is left to its own shelf. */
export const SHELF_LIMIT = 6;

/**
 * The five rows, from the library and what this viewer has watched.
 *
 * `progress` is the store's rows, newest first; `watchedAt` answers when a
 * set was finished, or `null`. They arrive as arguments rather than as an
 * import so this module can be tested without a store, and so the page
 * cannot accidentally read a third source of truth.
 *
 * @param {{library: any, byId: Map<string, any>, progress?: any[],
 *          watchedAt?: (setId: string) => number|null, limit?: number}} from
 */
export function homeShelves({ library, byId, progress = [], watchedAt = () => null, limit = SHELF_LIMIT }) {
  const positions = new Map(progress.map((row) => [row.setId, row]));

  const underway = [];
  for (const collection of [...library.series, ...library.tutorials]) {
    const entry = nextInCollection(collection, positions, watchedAt);
    if (entry) underway.push(entry);
  }
  // The show touched most recently is the one most likely to be why this
  // page was opened.
  underway.sort((a, b) => b.touchedAt - a.touchedAt);
  const nextUp = underway.slice(0, limit);

  // One title is one card on this page. A show being watched would otherwise
  // appear twice within a single screen — as the episode in progress, and
  // again as the same episode under Next up.
  const shown = new Set(nextUp.map((entry) => entry.set.setId));
  const continues = progress
    .filter((row) => resumeAt(row) !== null && !shown.has(row.setId))
    .map((row) => byId.get(row.setId))
    .filter(Boolean)
    .slice(0, limit);

  return {
    continues,
    nextUp,
    latestMovies: byArrival(library.movies, addedAt).slice(0, limit),
    latestSeries: byArrival(library.series, newestIn).slice(0, limit),
    latestCourses: byArrival(library.tutorials, newestIn).slice(0, limit),
  };
}

/**
 * What to offer from one show or course, or `null` when it offers nothing.
 *
 * Four cases, in this order:
 *
 * 1. **Nothing watched** — not underway, so not on this row. It belongs to
 *    Latest, or to the shelf.
 * 2. **Something has a resume point** — the most recently touched of those.
 *    Being part-way through an episode beats whatever follows a finished
 *    one: the viewer is in the middle of *that* episode.
 * 3. **Everything watched is finished** — walk forward from the one finished
 *    most recently and take the first episode not already watched. Walking
 *    forward rather than searching from the start is what stops an episode
 *    seen out of order from pulling the show backwards.
 * 4. **Nothing follows** — the show is finished and leaves the row.
 *
 * A glance is not being underway. `resumeAt` already refuses a position in
 * the first half-minute, and a show whose only mark is one of those is
 * treated as untouched rather than offered a card it cannot caption.
 */
function nextInCollection(collection, positions, watchedAt) {
  const ordered = flattenCollection(collection);

  let resumeSet = null;
  let resumeTouch = -Infinity;
  let finishedSet = null;
  let finishedTouch = -Infinity;

  for (const set of ordered) {
    const row = positions.get(set.setId);
    if (row && resumeAt(row) !== null && row.updatedAt > resumeTouch) {
      resumeTouch = row.updatedAt;
      resumeSet = set;
    }
    const finished = watchedAt(set.setId);
    if (finished !== null && finished > finishedTouch) {
      finishedTouch = finished;
      finishedSet = set;
    }
  }

  const touchedAt = Math.max(resumeTouch, finishedTouch);
  if (!Number.isFinite(touchedAt)) return null;

  if (resumeSet) return { set: resumeSet, collection, resume: true, touchedAt };

  // `nextAfter` walks the collection in the order its pages do, and returns
  // `null` at the end of one — so this terminates on a finished show.
  let candidate = nextAfter(collection, finishedSet.setId);
  while (candidate && watchedAt(candidate.setId) !== null) {
    candidate = nextAfter(collection, candidate.setId);
  }
  if (!candidate) return null;
  return { set: candidate, collection, resume: false, touchedAt };
}

/** When a set arrived. Absent in a payload from an older player. */
const addedAt = (set) => Number(set?.addedAt) || 0;

/**
 * When a collection last gained something.
 *
 * Its newest episode, not its first: a series still being uploaded keeps its
 * place on Latest, and one finished two years ago does not hold the top of
 * the row for having been started recently.
 */
function newestIn(collection) {
  let newest = 0;
  for (const set of flattenCollection(collection)) {
    const at = addedAt(set);
    if (at > newest) newest = at;
  }
  return newest;
}

/** Newest first, on a copy — the caller's shelves keep their own order. */
function byArrival(entries, when) {
  return [...entries].sort((a, b) => when(b) - when(a));
}
