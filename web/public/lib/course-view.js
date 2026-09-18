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
import { lessonsUnder, levelEntries } from "./library.js";
import { transcodeBadge } from "./set-badge.js";

/**
 * How far a folder may indent. The real course reaches four levels; past
 * that the indent costs more width than the nesting is worth saying.
 */
const MAX_INDENT = 3;

/** One playable row. */
function lessonRow(set, onPlay) {
  const row = el("button", "row");
  row.append(el("div", "num", episodeLabel(set)));

  const title = el("div", "title");
  title.append(el("b", null, set.title ?? set.setId));
  title.append(el("span", null, codecLine(set)));
  row.append(title);

  const badge = transcodeBadge(set);
  if (badge) row.append(badge);
  if (set.hasSummary) row.append(el("span", "has-summary", "notes"));

  row.append(
    el("div", "meta", [humanDuration(set.duration), humanSize(set.total)].filter(Boolean).join(" · ")),
  );
  row.addEventListener("click", () => onPlay(set));
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

  row.append(el("div", "meta", countOf(lessonsUnder(division), "lesson")));
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
    block.append(
      entry.kind === "lesson" ? lessonRow(entry.set, onPlay) : folderRow(entry.division, onOpen),
    );
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

  for (const set of division.items) block.append(lessonRow(set, onPlay));
  // Lessons first, then the folders below them: a lesson sitting in this
  // folder comes before a subfolder in every course this reads.
  for (const child of division.children) block.append(divisionBlock(child, depth + 1, onPlay));

  return block;
}
