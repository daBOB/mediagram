/**
 * A show's page as a wall of seasons: which shows get one, and what each
 * season's plate shows.
 */

import { describe, expect, test } from "bun:test";

import { hasSeasonWall, seasonNamed, seasonPlate } from "../public/lib/catalog/season-wall.js";

const ep = (fields: Record<string, unknown>) => ({ kind: "ep", poster: null, seasonPoster: null, ...fields });
const season = (title: string, items: unknown[]) => ({ title, season: null, items, children: [] });

describe("which shows open on a wall", () => {
  test("a show of several seasons does", () => {
    expect(hasSeasonWall({ divisions: [season("Season 1", []), season("Season 2", [])] })).toBe(true);
  });

  test("a show of one season goes straight to its episodes", () => {
    expect(hasSeasonWall({ divisions: [season("Season 1", [])] })).toBe(false);
  });
});

describe("a season's plate", () => {
  test("uses the season's own artwork and counts its episodes", () => {
    const plate = seasonPlate(
      season("Season 2", [ep({ poster: "tmdb-tv-1", seasonPoster: "tmdb-tv-1-s2" }), ep({})]),
    );
    expect(plate).toEqual({ name: "Season 2", meta: "two episodes", poster: "tmdb-tv-1-s2" });
  });

  test("falls back to the show's artwork when the season has none", () => {
    expect(seasonPlate(season("Season 3", [ep({ poster: "tmdb-tv-1" })])).poster).toBe("tmdb-tv-1");
  });

  test("falls back to nothing, and so to initials, when neither exists", () => {
    expect(seasonPlate(season("Episodes", [ep({})])).poster).toBeNull();
  });
});

test("a season is found by the title its plate carried", () => {
  const show = { divisions: [season("Season 1", []), season("Season 2", [])] };
  expect(seasonNamed(show, "Season 2")?.title).toBe("Season 2");
  expect(seasonNamed(show, "Season 9")).toBeNull();
});
