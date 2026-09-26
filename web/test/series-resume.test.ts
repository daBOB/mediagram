import { describe, expect, test } from "bun:test";
import { episodeShort, seriesResume } from "../public/lib/catalog/series-resume.js";

const ep = (season: number, episode: number) => ({ setId: `s${season}e${episode}`, kind: "ep", show: "Show", season, episode: String(episode), title: `Ep ${season}.${episode}` });
// Only the fields the rule reads; a real set carries many more.
const show = {
  divisions: [
    { title: "Season 1", season: 1, items: [ep(1, 1), ep(1, 2)], children: [] },
    { title: "Season 2", season: 2, items: [ep(2, 1), ep(2, 2)], children: [] },
  ],
} as unknown as Parameters<typeof seriesResume>[0];
const none = { resumeOf: () => null, recent: [] as string[], watched: () => false };

describe("where a show carries on", () => {
  test("a fresh show starts at its first episode", () => {
    const pick = seriesResume(show, none)!;
    expect([pick.set.setId, pick.verb, pick.at]).toEqual(["s1e1", "Play", null]);
  });

  test("the episode stopped partway through wins, even behind a finished one", () => {
    const pick = seriesResume(show, {
      resumeOf: (id) => (id === "s1e2" ? 600 : null),
      recent: ["other-show-ep", "s1e2"],
      watched: (id) => id === "s2e1",
    })!;
    expect([pick.set.setId, pick.verb, pick.at]).toEqual(["s1e2", "Resume", 600]);
  });

  test("otherwise the one after the furthest finished, across a season", () => {
    const pick = seriesResume(show, { ...none, watched: (id) => id === "s1e1" || id === "s1e2" })!;
    expect([pick.set.setId, pick.verb]).toEqual(["s2e1", "Continue"]);
  });

  test("a show watched to the end offers its start again", () => {
    const pick = seriesResume(show, { ...none, watched: () => true })!;
    expect([pick.set.setId, pick.verb]).toEqual(["s1e1", "Play"]);
  });

  test("an empty show offers nothing", () => {
    expect(seriesResume({ divisions: [] } as unknown as Parameters<typeof seriesResume>[0], none)).toBeNull();
  });

  test("the short label", () => {
    expect(episodeShort(ep(3, 15))).toBe("S3 E15");
    expect(episodeShort({ title: "Pilot" } as never)).toBe("Pilot");
  });
});
