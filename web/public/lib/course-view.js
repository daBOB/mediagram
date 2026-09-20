/**
 * Rendering a course the shape it actually has.
 *
 * A course nests, and nests unevenly: "Ausbildung Trading" holds
 * "1. Grundlagen" holds "3. Signal" holds twenty lessons and one more folder.
 * There are two ways to show that, and this module holds both because the two
 * containers that nest do not nest alike.
 *
 * `levelBlock` is one floor of the building: the lessons in this folder and
 * the doors to the ones below it. A course is 162 lessons across four levels,
 * and putting all of it on one page means a viewer scrolls past a hundred
 * things they did not ask for to reach the folder they did.
 *
 * `divisionBlock` is the whole tree at once, indented. A show's seasons are
 * one flat level with nothing under them, so there is nothing to walk into
 * and a drill-down would only add a click.
 */

import { el } from "./dom.js";
import { codecLine, countOf, episodeLabel, humanDuration, humanSize } from "./format.js";
import { countsUnder, isDocument, levelEntries } from "./library.js";
import { offlineBadge, transcodeBadge, watchedTick } from "./set-badge.js";

/**
 * How far a folder may indent. The real course reaches four levels; past
 * that the indent costs more width than the nesting is worth saying.
 */
const MAX_INDENT = 3;

/**
 * `n lessons · m documents`, or just the lessons where there are none.
 *
 * Stated once and exported, because the heading of a level and the row that
 * opens that level both say it and would otherwise drift the first time a
 * third kind of thing appears in a course.
 */
export function extentOf(division) {
  const { lessons, documents } = countsUnder(division);
  return [countOf(lessons, "lesson"), documents > 0 ? countOf(documents, "document") : null]
    .filter(Boolean)
    .join(" · ");
}

/** One playable row. */
function lessonRow(set, onPlay) {
  const row = el("button", "row");
  row.append(el("div", "num", episodeLabel(set)));

  const title = el("div", "title");
  const name = el("b", null, set.title ?? set.setId);
  // Before the title rather than after it: a column of ticks down the left of
  // a season reads at a glance, where one trailing each name does not.
  const tick = watchedTick(set);
  if (tick) name.prepend(tick);
  title.append(name);
  title.append(el("span", null, codecLine(set)));
  row.append(title);

  // Lessons get the badge too. A course has no artwork and the least to say
  // for itself on a shelf, and it is also the thing most likely to be held in
  // full — a lesson is a few hundred megabytes, so one watch caches all of it.
  for (const badge of [offlineBadge(set), transcodeBadge(set)]) {
    if (badge) row.append(badge);
  }
  if (set.hasSummary) row.append(el("span", "has-summary", "notes"));

  row.append(
    el("div", "meta", [humanDuration(set.duration), humanSize(set.total)].filter(Boolean).join(" · ")),
  );
  row.addEventListener("click", () => onPlay(set));
  return row;
}

/**
 * One document row: a link rather than a button.
 *
 * A link because that is what a document is — the browser opens a PDF in its
 * own viewer, and a viewer who wants it on disk already knows how to ask a
 * link for that. Routing it through the player instead would mean building a
 * second viewer for a thing browsers already display.
 *
 * `target` and `rel` together: the course page keeps its place, and the new
 * tab gets no handle back onto it.
 */
function documentRow(set) {
  const row = el("a", "row document");
  row.href = `/api/sets/${encodeURIComponent(set.setId)}/stream`;
  row.target = "_blank";
  row.rel = "noopener";

  row.append(el("div", "num", episodeLabel(set)));

  const title = el("div", "title");
  title.append(el("b", null, set.title ?? set.setId));
  title.append(el("span", null, set.container.toUpperCase()));
  row.append(title);

  // The word, not an icon: this row sits among lessons that look almost
  // exactly like it, and the one thing a viewer needs is which is which.
  row.append(el("span", "is-document", "document"));
  row.append(el("div", "meta", humanSize(set.total)));
  return row;
}

/** One folder: a door, with the size of the room behind it. */
function folderRow(division, onOpen) {
  const row = el("button", "row folder");
  // Empty, but present: it holds the call-number column so a folder and a
  // lesson in the same list start their titles at the same place.
  row.append(el("div", "num", ""));

  const title = el("div", "title");
  title.append(el("b", null, division.title));
  // Only when there is another floor below this one. "Twelve lessons" is
  // already said on the right; "three folders" is the thing it cannot say.
  if (division.children.length > 0) {
    title.append(el("span", null, countOf(division.children.length, "folder")));
  }
  row.append(title);

  row.append(el("div", "meta", extentOf(division)));
  row.append(el("span", "chevron", "\u203a"));

  row.addEventListener("click", () => onOpen(division.title));
  return row;
}

/**
 * One level of a course: its lessons and its folders, in the course's order.
 *
 * The order itself is `levelEntries`, in `library.js`, with the rest of the
 * shapes derived from the catalog — a render loop is the one place a rule
 * like that cannot be tested.
 */
export function levelBlock(level, onOpen, onPlay) {
  const block = el("section", "level");
  for (const entry of levelEntries(level)) {
    if (entry.kind === "lesson") block.append(lessonRow(entry.set, onPlay));
    else if (entry.kind === "document") block.append(documentRow(entry.set));
    else block.append(folderRow(entry.division, onOpen));
  }
  return block;
}

/**
 * One folder and everything under it.
 *
 * Depth is carried into the class name rather than measured from the DOM, so
 * the stylesheet decides how far in each level sits and this stays about
 * structure.
 */
export function divisionBlock(division, depth, onPlay) {
  const block = el("section", `season depth-${Math.min(depth, MAX_INDENT)}`);

  const head = el("h2");
  head.append(document.createTextNode(division.title));
  // Only a folder holding lessons gets a count; a folder of folders would be
  // counting its children's children, which says nothing useful.
  if (division.items.length > 0) {
    head.append(el("span", "count", ` · ${division.items.length}`));
  }
  block.append(head);

  for (const set of division.items) {
    block.append(isDocument(set) ? documentRow(set) : lessonRow(set, onPlay));
  }
  // Lessons first, then the folders below them: a lesson sitting in this
  // folder comes before a subfolder in every course this reads.
  for (const child of division.children) block.append(divisionBlock(child, depth + 1, onPlay));

  return block;
}
