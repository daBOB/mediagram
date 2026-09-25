/**
 * The search results view.
 *
 * A flat, ranked list rather than the shelves' grouping: the point of
 * searching a hundred and seventy lessons named "Definition" and "Mobile App"
 * is that the best answer is first, and grouping by shelf would bury it under
 * a heading.
 *
 * Every row says *where* it sits and *why* it matched, because the title
 * alone rarely distinguishes one lesson from the next.
 */

import { el } from "../dom.js";
import { codecLine, countOf, episodeLabel, humanDuration, humanSize } from "../format.js";
import { offlineBadge, progressRuleFor, watchedTick } from "./set-badge.js";

/** How a hit earned its place, in words rather than a field name. */
const WHY = {
  title: null,
  show: "matched the series or course",
  chap: "matched the chapter",
  path: "matched the folder",
  summary: "found in the summary",
};

/** Where a hit sits, as a person would say it. */
function locationOf(hit) {
  if (hit.kind === "ep") {
    return [hit.show, episodeLabel(hit)].filter(Boolean).join(" · ");
  }
  if (hit.kind === "tut") {
    // The folder path reads better than the generated chapter label, and is
    // what the shelves show too.
    const where = hit.path ?? hit.chap;
    return [hit.show, where].filter(Boolean).join(" · ");
  }
  return [hit.year].filter(Boolean).join(" · ");
}

/**
 * Renders `hits` into `main`.
 *
 * `onPlay` rather than a link: a hit is a set, and opening one is the same
 * dialog the shelves open.
 */
export function renderSearch(main, query, hits, onPlay) {
  const head = el("header", "shelf-head");
  head.append(el("h1", null, `“${query}”`));
  // The same block the shelves use, so a result list is a page of the
  // catalogue rather than a different screen.
  head.append(el("p", "sub", hits.length === 0 ? "nothing found" : countOf(hits.length, "result")));
  main.append(head);
  if (hits.length === 0) {
    main.append(el("p", "empty", "No title, folder or summary in the library mentions that."));
    return;
  }

  const block = el("section", "season");
  for (const hit of hits) {
    const row = el("button", "row");
    row.append(el("div", "num", episodeLabel(hit) || ""));

    const title = el("div", "title");
    const name = el("b", null, hit.title ?? hit.setId);
    const tick = watchedTick(hit);
    if (tick) name.prepend(tick);
    title.append(name);
    const where = locationOf(hit);
    if (where) title.append(el("span", null, where));
    // The excerpt is the reason a summary hit is worth showing at all.
    if (hit.excerpt) title.append(el("span", "excerpt", hit.excerpt));
    row.append(title);

    const why = WHY[hit.matched];
    if (why) row.append(el("span", "badge", why));
    // After the reason it matched, which is what the viewer came here for.
    const held = offlineBadge(hit);
    if (held) row.append(held);

    row.append(
      el(
        "div",
        "meta",
        [codecLine(hit), humanDuration(hit.duration), humanSize(hit.total)].filter(Boolean).join(" · "),
      ),
    );
    const progress = progressRuleFor(hit);
    if (progress) row.append(progress);

    row.addEventListener("click", () => onPlay(hit));
    block.append(row);
  }
  main.append(block);
}
