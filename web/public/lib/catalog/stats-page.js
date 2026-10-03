/**
 * The Stats page: this profile's watching this week, this month and ever, the
 * last thirty days as bars, and every start, restart and finish, newest first.
 *
 * Asked of the server each time it opens — the minutes live there, counted
 * from every device — and drawn only if the viewer is still on the page when
 * the answer lands. Each profile sees only its own.
 */

import { el } from "../dom.js";
import { profileId } from "../watch-state.js";
import { heading } from "./shelf-view.js";
import { historyLine, shortDate, watchTime, weekdayInitial, whenLabel } from "./stats-format.js";
import { achievementsSection } from "./stats-achievements.js";
import { markSeen } from "./stats-dot.js";

/**
 * @param {HTMLElement} main
 * @param {{ byId: Map<string, any> }} context
 * @param {() => boolean} stillHere
 */
export async function renderStats(main, { byId }, stillHere) {
  heading(main, "Stats");
  const id = profileId();
  if (id === null) return main.append(el("p", "empty", "Nothing watched yet."));
  let summary;
  try {
    const response = await fetch(`/api/profiles/${encodeURIComponent(id)}/stats`);
    if (!response.ok) throw new Error(`the server answered ${response.status}`);
    summary = await response.json();
  } catch (error) {
    if (stillHere()) main.append(el("p", "error", `Could not read your stats: ${error.message}`));
    return;
  }
  if (!stillHere()) return;
  if (summary.history.length === 0) return main.append(el("p", "empty", "Nothing watched yet."));
  const now = Date.now();
  main.append(totals(summary), lastThirty(summary.last30));
  // Before the history, which has no end; nothing at all while there is nothing in it.
  const { earned, next } = summary.achievements;
  if (earned.length + next.length > 0) main.append(achievementsSection(summary.achievements, (at) => whenLabel(at, now)));
  main.append(history(summary.history, byId, now));
  // Drawn, so shown: what this page holds is no longer news on this browser.
  markSeen(id, earned);
}

function totals(summary) {
  const row = el("dl", "stats-totals");
  for (const [label, seconds] of [
    ["This week", summary.weekSeconds],
    ["This month", summary.monthSeconds],
    ["All time", summary.allSeconds],
  ]) {
    const item = el("div", "stats-total");
    item.append(el("dt", null, label), el("dd", null, watchTime(seconds)));
    row.append(item);
  }
  return row;
}

/** One bar per day, today rightmost, each as tall as its share of the busiest. */
function lastThirty(days) {
  const section = el("section", "stats-days");
  section.append(el("h2", "shelf-sub", "Last 30 days"));
  const bars = el("div", "stats-bars");
  bars.setAttribute("role", "list");
  const most = Math.max(0, ...days.map((entry) => entry.seconds));
  for (const entry of days) {
    const day = el("div", "stats-day");
    day.setAttribute("role", "listitem");
    day.title = `${shortDate(entry.day)} · ${watchTime(entry.seconds)}`;
    day.setAttribute("aria-label", day.title);
    const bar = el("span", "stats-bar");
    bar.style.height = `${most > 0 ? (entry.seconds / most) * 100 : 0}%`;
    day.append(bar, el("span", "stats-weekday", weekdayInitial(entry.day)));
    bars.append(day);
  }
  section.append(bars);
  return section;
}

/** Every entry; the stylesheet's `content-visibility` keeps a long one cheap. */
function history(entries, byId, now) {
  const section = el("section", "stats-history");
  section.append(el("h2", "shelf-sub", "History"));
  const list = el("ol", "stats-history-list");
  for (const entry of entries) list.append(el("li", null, historyLine(entry, byId.get(entry.setId) ?? null, now)));
  section.append(list);
  return section;
}
