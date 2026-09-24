import { describe, expect, test } from "bun:test";
import { FEATURED_COUNT, pickFeatured, stepFrom } from "../public/lib/catalog/featured-picks.js";

const film = (setId: string, poster: string | null = `tmdb-movie-${setId}`) => ({ setId, poster });
// A source that always answers the same fraction, so a shuffle is repeatable.
const fixed = (value: number) => () => value;

describe("pickFeatured", () => {
  test("leaves out films already watched and films with no poster", () => {
    const movies = [film("a"), film("b"), film("c", null), film("d")];
    const picks = pickFeatured(movies, (setId: string) => setId === "b", fixed(0));
    expect(picks.map((set) => set.setId).sort()).toEqual(["a", "d"]);
  });

  test("stops at the reel's length", () => {
    const movies = Array.from({ length: 40 }, (_, index) => film(`f${index}`));
    expect(pickFeatured(movies, () => false, Math.random)).toHaveLength(FEATURED_COUNT);
    expect(FEATURED_COUNT).toBe(12);
  });

  test("the order follows the random source, and the library is left as it was", () => {
    const movies = ["a", "b", "c", "d"].map((id) => film(id));
    const before = movies.map((set) => set.setId);
    const first = pickFeatured(movies, () => false, fixed(0)).map((set) => set.setId);
    const again = pickFeatured(movies, () => false, fixed(0)).map((set) => set.setId);
    expect(first).toEqual(again);
    expect(first).not.toEqual(before);
    expect(movies.map((set) => set.setId)).toEqual(before);
  });

  test("nothing to feature is an empty reel", () => {
    expect(pickFeatured([film("a")], () => true, Math.random)).toEqual([]);
  });
});

describe("stepFrom", () => {
  test("wraps at both ends", () => {
    expect(stepFrom(0, 1, 3)).toBe(1);
    expect(stepFrom(2, 1, 3)).toBe(0);
    expect(stepFrom(0, -1, 3)).toBe(2);
  });
});
