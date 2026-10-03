/** The Stats page's words: durations, times, names and history lines. */

import { describe, expect, test } from "bun:test";
import { historyLine, historyTitle, shortDate, watchTime, weekdayInitial, whenLabel } from "../public/lib/catalog/stats-format.js";

describe("watchTime", () => {
  test.each([
    [0, "under a minute"],
    [59.9, "under a minute"],
    [60, "1 min"],
    [42 * 60 + 59, "42 min"],
    [59 * 60, "59 min"],
    [3600, "1 h"],
    [3 * 3600, "3 h"],
    [3 * 3600 + 12 * 60 + 59, "3 h 12 min"],
    [250 * 3600 + 60, "250 h 1 min"],
  ])("%p seconds is %p", (seconds, words) => {
    expect(watchTime(seconds)).toBe(words);
  });
});

describe("whenLabel", () => {
  // Saturday 3 October 2026, 21:30 on this machine's clock.
  const now = new Date(2026, 9, 3, 21, 30).getTime();
  test.each([
    ["earlier today", new Date(2026, 9, 3, 0, 10), "today 00:10"],
    ["late last night", new Date(2026, 9, 2, 23, 50), "Fri 23:50"],
    ["six days ago", new Date(2026, 8, 27, 9, 5), "Sun 09:05"],
    ["seven days ago", new Date(2026, 8, 26, 21, 14), "26 Sep"],
    ["earlier this year", new Date(2026, 0, 2, 8, 0), "2 Jan"],
    ["another year", new Date(2025, 8, 21, 21, 14), "21 Sep 2025"],
    ["a clock ahead of this one, on a later day", new Date(2026, 9, 5, 9, 0), "5 Oct"],
  ])("%s", (_label, then, words) => {
    expect(whenLabel(then.getTime(), now)).toBe(words);
  });
});

test("calendar days, not 24-hour spans, across the clocks going back", () => {
  // In a zone with daylight saving (Europe's ends 25 October 2026) one of
  // these days is 25 hours long; elsewhere this is an ordinary week.
  const now = new Date(2026, 9, 27, 0, 30).getTime();
  expect(whenLabel(new Date(2026, 9, 21, 23, 59).getTime(), now)).toBe("Wed 23:59");
  expect(whenLabel(new Date(2026, 9, 20, 23, 59).getTime(), now)).toBe("20 Oct");
});

test("a day's date and weekday initial", () => {
  expect(shortDate("2026-10-03")).toBe("3 Oct");
  expect(weekdayInitial("2026-10-03")).toBe("S");
  expect(weekdayInitial("2026-09-28")).toBe("M");
});

describe("historyTitle names a set the way Continue watching does", () => {
  test("a film by its title", () => {
    expect(historyTitle({ kind: "movie", title: "Der Pate", show: null, setId: "01A" })).toBe("Der Pate");
  });
  test("an episode by its show and number", () => {
    expect(historyTitle({ kind: "ep", title: "Pilot", show: "Crime 101", season: 1, episode: "4", setId: "01B" })).toBe("Crime 101 S1E4");
  });
  test("a lesson by its course and number", () => {
    expect(historyTitle({ kind: "tut", title: "Zinsen", show: "Geldhochschule", episode: "3", setId: "01C" })).toBe("Geldhochschule 3");
  });
  test("a set the library no longer holds", () => {
    expect(historyTitle(null)).toBe("No longer in the library");
  });
});

describe("historyLine", () => {
  const now = new Date(2026, 9, 3, 21, 30).getTime();
  const film = { kind: "movie", title: "Der Pate", show: null, setId: "01A" };
  const saturday = new Date(2026, 9, 3, 21, 14).getTime();
  const friday = new Date(2026, 9, 2, 20, 0).getTime();

  test.each([
    [{ kind: "started", at: friday, seconds: 42 * 60 }, "Started · Der Pate · Fri 20:00 · 42 min"],
    [{ kind: "again", at: saturday, seconds: 3 * 3600 }, "Watched again · Der Pate · today 21:14 · 3 h"],
    [{ kind: "finished", at: saturday, seconds: 30 }, "Finished · Der Pate · today 21:14 · under a minute"],
    // Finished before stats existed: nothing was counted, so no duration is claimed.
    [{ kind: "finished", at: friday, seconds: 0 }, "Finished · Der Pate · Fri 20:00"],
  ] as const)("%j", (entry, line) => {
    expect(historyLine(entry, film, now)).toBe(line);
  });
});
