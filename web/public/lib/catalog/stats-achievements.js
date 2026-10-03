/**
 * The Stats page's Achievements section: what this profile has earned, with
 * the day it was earned, and the few closest to come, with how far along
 * each is. The server works both out (`src/state/achievements.ts`); this only
 * names and draws them. The Android pages name them through a port of
 * {@link achievementLabel} and {@link progressLine}, held to these by
 * `test/fixtures/watch-state/achievement-labels.json`.
 */

import { el } from "../dom.js";

const RUNG = /^([a-z]+)-(\d+)$/;

/** What each ladder's rung is called, from the rung's number. */
const NAMES = new Map([
  ["films", (n) => (n === "1" ? "First film" : `${n} films`)],
  ["genres", (n) => `${n} genres`],
  ["docs", (n) => `${n} documentaries`],
  ["hours", (n) => `${n} hours`],
  ["streak", (n) => `${n}-day streak`],
  ["binge", (n) => `${n} episodes in a day`],
]);

/**
 * What a progress line counts. A whole series names nothing: its count is a
 * show's episodes as often as a course's lessons.
 */
const UNITS = new Map([
  ["films", "films"],
  ["genres", "genres"],
  ["docs", "documentaries"],
  ["hours", "hours"],
  ["streak", "days"],
  ["binge", "episodes"],
]);

/** An achievement's name. An id this build does not know shows as itself rather than vanishing. */
export function achievementLabel(id) {
  if (id === "whole-show") return "A whole series";
  const [, ladder = "", n = ""] = RUNG.exec(id) ?? [];
  return NAMES.get(ladder)?.(n) ?? id;
}

/** How far along one still to come is: "7 of 10 films". */
export function progressLine({ id, have, need }) {
  const ladder = RUNG.exec(id)?.[1] ?? "";
  const unit = ladder === "films" && need === 1 ? "film" : UNITS.get(ladder);
  return unit ? `${have} of ${need} ${unit}` : `${have} of ${need}`;
}

/**
 * The day an achievement was earned, in the history's own words without
 * their clock time: a day-based achievement is dated at that day's noon,
 * and "Sat 12:00" would read as a moment rather than a day.
 */
const earnedOn = (when, at) => when(at).replace(/ \d{1,2}:\d{2}$/, "");

/**
 * @param {{earned: {id: string, earnedAt: number}[], next: {id: string, have: number, need: number}[]}} achievements
 * @param {(at: number) => string} when the history's own time wording: "today 21:14", "Sat 21:14", "21 Sep"
 */
export function achievementsSection(achievements, when) {
  const section = el("section", "stats-achievements");
  section.append(el("h2", "shelf-sub", "Achievements"));
  if (achievements.earned.length > 0) {
    const list = el("ul", "achievements-earned");
    for (const { id, earnedAt } of achievements.earned) {
      const row = el("li");
      row.append(el("span", "achievement-name", achievementLabel(id)), el("span", "achievement-when", earnedOn(when, earnedAt)));
      list.append(row);
    }
    section.append(list);
  }
  if (achievements.next.length > 0) {
    section.append(el("h3", null, "Next"));
    const list = el("ul", "achievements-next");
    for (const step of achievements.next) {
      const row = el("li");
      // The words beside it already say it; the bar is for the eye alone.
      const bar = el("progress");
      bar.max = step.need;
      bar.value = step.have;
      bar.setAttribute("aria-hidden", "true");
      row.append(el("span", "achievement-name", achievementLabel(step.id)), el("span", "achievement-progress", progressLine(step)), bar);
      list.append(row);
    }
    section.append(list);
  }
  return section;
}
