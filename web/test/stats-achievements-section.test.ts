/** The Stats page's Achievements section, as drawn. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { achievementLabel, achievementsSection, progressLine } from "../public/lib/catalog/stats-achievements.js";
import { whenLabel } from "../public/lib/catalog/stats-format.js";
import { achievements } from "../src/state/achievements";
import { utcOffsetMinutes } from "../src/state/stats-recorder";
import { browserEnvironment, type Node } from "./support/player-environment";
import { descendants, textOf } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => { env = browserEnvironment(); });
afterEach(() => env.restore());

/** The history's wording for three instants, the way the page's own formatter words them. */
const when = (at: number) => ({ 1: "today 08:15", 2: "Wed 00:00", 3: "21 Sep", 4: "21 Sep 2025" })[at] ?? "?";
/** The fake document's nodes, under the DOM type the module's JSDoc declares. */
const nodesOf = (section: unknown) => descendants(section as Node);
const byClass = (section: unknown, name: string) =>
  nodesOf(section).filter((node) => node.className === name).map((node) => textOf(node));

describe("the Achievements section", () => {
  test("earned achievements carry the day they were earned, without a clock time", () => {
    const section = achievementsSection({
      earned: [{ id: "films-1", earnedAt: 1 }, { id: "streak-7", earnedAt: 2 }, { id: "genres-5", earnedAt: 3 }, { id: "docs-10", earnedAt: 4 }],
      next: [],
    }, when);
    expect(byClass(section, "achievement-name")).toEqual(["First film", "7-day streak", "5 genres", "10 documentaries"]);
    expect(byClass(section, "achievement-when")).toEqual(["today", "Wed", "21 Sep", "21 Sep 2025"]);
  });

  test("the next ones carry how far along each is, words and bar", () => {
    const section = achievementsSection({ earned: [], next: [{ id: "films-10", have: 7, need: 10 }] }, when);
    expect(byClass(section, "achievement-progress")).toEqual(["7 of 10 films"]);
    const bar = nodesOf(section).find((node) => node.tagName === "PROGRESS")!;
    expect([Number(bar.value), Number(bar.max)]).toEqual([7, 10]);
    expect(bar.attributes.get("aria-hidden")).toBe("true");
  });

  test("nothing earned yet draws no earned list, only what is next", () => {
    const section = achievementsSection({ earned: [], next: [{ id: "films-1", have: 0, need: 1 }] }, when);
    expect(byClass(section, "achievements-earned")).toEqual([]);
    expect(nodesOf(section).some((node) => node.tagName === "H3" && node.textContent === "Next")).toBe(true);
  });

  test("names and progress for one id only this build would not know", () => {
    expect(achievementLabel("marathon-3")).toBe("marathon-3");
    expect(progressLine({ id: "marathon-3", have: 1, need: 3 })).toBe("1 of 3");
  });

  test("a winter streak read on a summer evening in Berlin shows the day that completed it", () => {
    const before = process.env.TZ;
    try {
      process.env.TZ = "Europe/Berlin";
      const now = Date.parse("2026-10-03T19:30:00Z");
      const days = Array.from({ length: 7 }, (_, i) => ({ day: `2026-01-1${i}`, device: "phone-1", seconds: 600, updatedAt: 1 }));
      const answer = achievements({ today: "2026-10-03", utcOffsetMinutes: utcOffsetMinutes(now), kids: false, days, watched: [], library: [], collections: [] });
      expect(answer.earned.map((achievement) => achievement.id)).toEqual(["streak-7"]);
      const section = achievementsSection(answer, (at) => whenLabel(at, now));
      expect(byClass(section, "achievement-when")).toEqual(["16 Jan"]);
    } finally {
      // Bun reads TZ on every date call; leaving it set would move every later test's clock.
      if (before === undefined) delete process.env.TZ;
      else process.env.TZ = before;
    }
  });
});
