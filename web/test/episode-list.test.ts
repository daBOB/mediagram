/**
 * What the episode sidebar lists: a show by its seasons, a course by the
 * folders its page walks, the season the title is in opened first — and a
 * messy run still listed, with what the index could not place kept last.
 */

import { describe, expect, test } from "bun:test";
import { catalogSet as set } from "./support/catalog-set";
import { groupDepartments } from "../public/lib/departments.js";
import { episodeGroups } from "../public/lib/playback/episode-list.js";
import { collectionOf, playsNext } from "../public/lib/playback/plays-next.js";
import type { CatalogSet } from "../public/lib/library.js";

const shape = (model: ReturnType<typeof episodeGroups>) =>
  model && { at: model.at, groups: model.groups.map((group) => [group.title, group.items.map((item) => item.setId)]) };
const runOf = (sets: CatalogSet[], current: CatalogSet) => episodeGroups(collectionOf(groupDepartments(sets), current), current.setId);

describe("a show", () => {
  const ep = (id: string, season: number, episode: string) => set({ setId: id, kind: "ep", show: "Star City", season, episode, title: id });
  const sets = [ep("s2e1", 2, "1"), ep("s1e2", 1, "2"), ep("s1e1", 1, "1"), ep("s2e2", 2, "2")];

  test("lists every season in number order and opens on the one playing", () => {
    expect(shape(runOf(sets, sets[3]!))).toEqual({
      at: 1,
      groups: [["Season 1", ["s1e1", "s1e2"]], ["Season 2", ["s2e1", "s2e2"]]],
    });
  });

  test("one season is one group", () => {
    expect(shape(runOf(sets.slice(1, 3), sets[1]!))).toEqual({ at: 0, groups: [["Season 1", ["s1e1", "s1e2"]]] });
  });
});

describe("a course", () => {
  const lesson = (id: string, path: string | null, episode: string, kind = "tut") =>
    set({ setId: id, kind, show: "Kurs", path, episode, title: id });

  test("lists the folders that hold lessons in the order its page walks them, documents left out", () => {
    const sets = [
      lesson("a1", "1. Basics", "1"), lesson("a2", "1. Basics", "2"),
      lesson("b1", "1. Basics/2. Deeper", "1"), lesson("w", "1. Basics", "3", "doc"),
      lesson("c1", "3. Last", "1"),
    ];
    expect(shape(runOf(sets, sets[4]!))).toEqual({
      at: 2,
      groups: [["1. Basics", ["a1", "a2"]], ["2. Deeper", ["b1"]], ["3. Last", ["c1"]]],
    });
  });

  test("a course without folders is one list", () => {
    const sets = [lesson("l2", null, "2"), lesson("l1", null, "1")];
    expect(shape(runOf(sets, sets[0]!))?.groups).toEqual([["Chapter 1", ["l1", "l2"]]]);
  });

  test("a course of documents alone has nothing to list", () => {
    const sets = [lesson("w1", "Workbooks", "1", "doc")];
    expect(runOf(sets, sets[0]!)).toBeNull();
  });
});

describe("no run", () => {
  test("a film has no list", () => {
    const film = set({ kind: "movie", title: "Blade" });
    expect(runOf([film], film)).toBeNull();
    expect(episodeGroups(null, film.setId)).toBeNull();
  });
});

describe("a messy run", () => {
  const sets = [
    set({ setId: "s1e1", kind: "ep", show: "Patchy", season: 1, episode: "1", title: "Pilot" }),
    set({ setId: "s1e2", kind: "ep", show: "Patchy", season: 1, episode: "2", title: "Second" }),
    set({ setId: "s2-nonumber", kind: "ep", show: "Patchy", season: 2, episode: null, title: "Special" }),
    set({ setId: "s2e1", kind: "ep", show: "Patchy", season: 2, episode: "1", title: "Return" }),
    set({ setId: "unplaced", kind: "ep", show: "Patchy", season: null, episode: null, title: null }),
  ];

  test("an episode without a number goes last in its season, one without a season after every season", () => {
    expect(shape(runOf(sets, sets[0]!))?.groups).toEqual([
      ["Season 1", ["s1e1", "s1e2"]],
      ["Season 2", ["s2e1", "s2-nonumber"]],
      ["Episodes", ["unplaced"]],
    ]);
  });

  test("a title the catalogue does not know still gets the list, opened on the first season", () => {
    const stranger = set({ setId: "not-in-the-index", kind: "ep", show: "Patchy", season: 7, episode: "7" });
    expect(shape(runOf(sets, stranger))?.at).toBe(0);
    expect(shape(runOf(sets, stranger))?.groups).toHaveLength(3);
  });

  test("and ⏮/⏭ still step through the run, the unplaced episode and the numberless one included", () => {
    const library = groupDepartments(sets);
    // The run keeps the order the show page plays in, where sorting by name
    // puts "Episodes" before "Season 1"; only the sidebar moves it last.
    const step = (at: CatalogSet) => {
      const { previous, next } = playsNext(library, at, null);
      return [previous?.setId ?? null, next?.setId ?? null];
    };
    expect(step(sets[4]!)).toEqual([null, "s1e1"]);
    expect(step(sets[0]!)).toEqual(["unplaced", "s1e2"]);
    expect(step(sets[2]!)).toEqual(["s2e1", null]);
  });
});
