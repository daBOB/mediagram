/**
 * Rendering a course the shape it actually has.
 *
 * A course nests, and nests unevenly: "Ausbildung Trading" holds
 * "Grundlagen" holds "Signal" holds fourteen lessons and one more folder.
 * Rendering that as one flat list of folders produces twenty-one headings
 * that each repeat the same two parents and say nothing about how the course
 * is built, so this walks the tree `library.js` derived and lets the nesting
 * show.
 */

import { el } from "./dom.js";
import { codecLine, episodeLabel, humanDuration, humanSize } from "./format.js";
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
