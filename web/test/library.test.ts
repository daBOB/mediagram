/**
 * Turning a flat catalog into the three shelves a viewer expects.
 *
 * The index stores one row per set and says what kind it is; the shape a
 * library page needs — films, shows with seasons, courses with chapters — is
 * derived, and derived in one tested place rather than inside a render loop.
 */

import { describe, expect, test } from "bun:test";
import {
  divisionAt,
  groupLibrary,
  lessonsUnder,
  levelEntries,
  nextAfter,
  type CatalogSet,
} from "../public/lib/library.js";

const set = (over: Record<string, unknown> = {}): CatalogSet => ({
  setId: `01SET${Math.random().toString(36).slice(2, 10).toUpperCase()}`,
  kind: "movie",
  title: "A Title",
  show: null,
  path: null,
  chap: null,
  season: null,
  episode: null,
  year: null,
  container: "mp4",
  vcodec: "h264",
  acodec: "aac",
  duration: 60,
  total: 1000,
  partCount: 1,
  ...over,
});

describe("shelves", () => {
  test("a film goes to films, not to shows", () => {
    const library = groupLibrary([set({ kind: "movie", title: "Blade" })]);

    expect(library.movies).toHaveLength(1);
    expect(library.movies[0]!.title).toBe("Blade");
    expect(library.series).toHaveLength(0);
    expect(library.tutorials).toHaveLength(0);
  });

  test("episodes gather under their show, then their season", () => {
    const library = groupLibrary([
      set({ kind: "ep", show: "Widow's Bay", season: 1, episode: "2", title: "Two" }),
      set({ kind: "ep", show: "Widow's Bay", season: 1, episode: "1", title: "One" }),
      set({ kind: "ep", show: "Widow's Bay", season: 2, episode: "1", title: "Next" }),
    ]);

    expect(library.series).toHaveLength(1);
    const show = library.series[0]!;
    expect(show.name).toBe("Widow's Bay");
    expect(show.count).toBe(3);
    expect(show.divisions.map((s) => s.season)).toEqual([1, 2]);
    expect(show.divisions[0]!.items.map((e) => e.title)).toEqual(["One", "Two"]);
  });

  test("lessons gather under their course, then their chapter", () => {
    const library = groupLibrary([
      set({ kind: "tut", show: "Geldhochschule", chap: "Basics", episode: "2", title: "B" }),
      set({ kind: "tut", show: "Geldhochschule", chap: "Basics", episode: "1", title: "A" }),
      set({ kind: "tut", show: "Geldhochschule", chap: "Later", episode: "1", title: "C" }),
    ]);

    expect(library.tutorials).toHaveLength(1);
    const course = library.tutorials[0]!;
    expect(course.name).toBe("Geldhochschule");
    expect(course.divisions.map((c) => c.title)).toEqual(["Basics", "Later"]);
    expect(course.divisions[0]!.items.map((i) => i.title)).toEqual(["A", "B"]);
  });

  /** The live library: no chapter titles, only a season number. */
  test("a course with no chapter titles falls back to its numbers", () => {
    const library = groupLibrary([
      set({ kind: "tut", show: "Geldhochschule", chap: null, season: 1, episode: "1" }),
      set({ kind: "tut", show: "Geldhochschule", chap: null, season: 1, episode: "2" }),
    ]);

    expect(library.tutorials[0]!.divisions[0]!.title).toBe("Chapter 1");
    expect(library.tutorials[0]!.divisions[0]!.items).toHaveLength(2);
  });

  test("episode numbers sort numerically, not as text", () => {
    const library = groupLibrary(
      ["10", "9", "1"].map((episode) =>
        set({ kind: "ep", show: "S", season: 1, episode, title: episode }),
      ),
    );

    expect(library.series[0]!.divisions[0]!.items.map((i) => i.title)).toEqual(["1", "9", "10"]);
  });

  test("shows and courses are listed alphabetically", () => {
    const library = groupLibrary([
      set({ kind: "ep", show: "Zed", season: 1, episode: "1" }),
      set({ kind: "ep", show: "Alpha", season: 1, episode: "1" }),
    ]);

    expect(library.series.map((s) => s.name)).toEqual(["Alpha", "Zed"]);
  });

  test("a set with no show still lands somewhere", () => {
    const library = groupLibrary([set({ kind: "ep", show: null, episode: "1" })]);

    expect(library.series).toHaveLength(1);
    expect(library.series[0]!.name).toBe("Unknown show");
  });

  test("an unknown kind is not silently dropped", () => {
    const library = groupLibrary([set({ kind: "something-new", title: "Odd" })]);

    expect(library.movies.map((m) => m.title)).toContain("Odd");
  });

  test("an empty catalog gives three empty shelves", () => {
    const library = groupLibrary([]);

    expect(library.movies).toEqual([]);
    expect(library.series).toEqual([]);
    expect(library.tutorials).toEqual([]);
  });
});

describe("folders, when a course carries them", () => {
  const lesson = (path: string | null, episode: string, title: string) => ({
    ...set({ kind: "tut", show: "Geldhochschule", chap: null, season: 1 }),
    path,
    episode,
    title,
  });

  /** Titles of a division and everything under it, as an indented outline. */
  const outline = (divisions: any[], depth = 0): string[] =>
    divisions.flatMap((d) => [
      "  ".repeat(depth) + d.title,
      ...outline(d.children, depth + 1),
    ]);

  /** The real course: uneven depth, and a folder holding videos beside one. */
  test("a course's folders nest, rather than flattening into one long list", () => {
    const library = groupLibrary([
      lesson("Basislektionen/1. Start", "1", "Begrüßung"),
      lesson("Ausbildung Trading/1. Grundlagen/1. Trading", "1", "Einführung"),
      lesson("Ausbildung Trading/1. Grundlagen/1. Trading", "2", "Definition"),
      lesson("Ausbildung Trading/1. Grundlagen/3. Signal", "1", "Muster"),
      lesson("Der erleuchtete Investor", "1", "Teil 1"),
    ]);

    const course = library.tutorials[0]!;
    expect(outline(course.divisions)).toEqual([
      "Ausbildung Trading",
      "  1. Grundlagen",
      "    1. Trading",
      "    3. Signal",
      "Basislektionen",
      "  1. Start",
      "Der erleuchtete Investor",
    ]);
    expect(course.count).toBe(5);
  });

  test("a folder names itself, not the whole path it sits at the end of", () => {
    // The old shape titled every division with its full path, so a course
    // read as twenty-one repetitions of "Ausbildung Trading / Grundlagen /".
    const library = groupLibrary([lesson("Ausbildung Trading/1. Grundlagen", "1", "Einführung")]);

    const top = library.tutorials[0]!.divisions[0]!;
    expect(top.title).toBe("Ausbildung Trading");
    expect(top.children[0]!.title).toBe("1. Grundlagen");
  });

  test("a folder holding lessons beside a subfolder keeps both", () => {
    // "3. Signal" holds fourteen lessons and a folder. Neither may hide the
    // other: the lessons are not in the subfolder, and the subfolder is not a
    // lesson.
    const library = groupLibrary([
      lesson("Ausbildung Trading/3. Signal", "1", "Muster"),
      lesson("Ausbildung Trading/3. Signal/14. Exkurs TWS", "1", "Exkurs"),
    ]);

    const signal = library.tutorials[0]!.divisions[0]!.children[0]!;
    expect(signal.title).toBe("3. Signal");
    expect(signal.items.map((i: any) => i.title)).toEqual(["Muster"]);
    expect(signal.children.map((c: any) => c.title)).toEqual(["14. Exkurs TWS"]);
    expect(signal.children[0]!.items.map((i: any) => i.title)).toEqual(["Exkurs"]);
  });

  test("a course counts the folders that actually hold lessons", () => {
    // What the shelf card means by "chapters". A parent that only contains
    // other folders is structure, not a chapter someone can open.
    const library = groupLibrary([
      lesson("A/1. One", "1", "First"),
      lesson("A/2. Two", "1", "Second"),
      lesson("B", "1", "Third"),
    ]);

    expect(library.tutorials[0]!.chapters).toBe(3);
  });

  test("folders sort by number where they have one, at every level", () => {
    const library = groupLibrary([
      lesson("Ausbildung/10. Zehn", "1", "a"),
      lesson("Ausbildung/2. Zwei", "1", "b"),
      lesson("Ausbildung/1. Eins", "1", "c"),
    ]);

    expect(library.tutorials[0]!.divisions[0]!.children.map((c: any) => c.title)).toEqual([
      "1. Eins",
      "2. Zwei",
      "10. Zehn",
    ]);
  });

  test("the path wins over the chapter number, which no longer describes the shape", () => {
    const library = groupLibrary([
      { ...lesson("Section A/Chapter 1", "1", "One"), season: 7 },
      { ...lesson("Section B/Chapter 1", "1", "Two"), season: 2 },
    ]);

    expect(library.tutorials[0]!.divisions.map((d: any) => d.title)).toEqual([
      "Section A",
      "Section B",
    ]);
  });

  test("a course uploaded before paths existed still divides by its numbers", () => {
    const library = groupLibrary([lesson(null, "1", "Old")]);

    const only = library.tutorials[0]!.divisions[0]!;
    expect(only.title).toBe("Chapter 1");
    expect(only.children).toEqual([]);
  });

  test("episodes use their folders too, when a show has them", () => {
    const library = groupLibrary([
      { ...set({ kind: "ep", show: "Widow's Bay", season: 1, episode: "1" }), path: "Season 1" },
    ]);

    expect(library.series[0]!.divisions[0]!.title).toBe("Season 1");
  });
});

/**
 * Walking into a course one floor at a time.
 *
 * Built to the shape the real course has: "Ausbildung Trading" holds
 * "1. Grundlagen" holds "3. Signal" holds lessons beside one more folder.
 */
describe("levels", () => {
  const course = () =>
    groupLibrary([
      set({ kind: "tut", show: "Kurs", path: "Ausbildung/1. Grundlagen/3. Signal", title: "A" }),
      set({ kind: "tut", show: "Kurs", path: "Ausbildung/1. Grundlagen/3. Signal", title: "B" }),
      set({
        kind: "tut",
        show: "Kurs",
        path: "Ausbildung/1. Grundlagen/3. Signal/14. Exkurs",
        title: "C",
      }),
      set({ kind: "tut", show: "Kurs", path: "Ausbildung/2. Fortgeschritten", title: "D" }),
      set({ kind: "tut", show: "Kurs", path: "Basis", title: "E" }),
    ]).tutorials[0]!;

  test("an empty trail stands in for the course itself", () => {
    const top = divisionAt(course().divisions, [])!;

    expect(top.title).toBeNull();
    expect(top.items).toHaveLength(0);
    expect(top.children.map((c) => c.title)).toEqual(["Ausbildung", "Basis"]);
  });

  test("a trail walks to the folder it names, and no further", () => {
    const level = divisionAt(course().divisions, ["Ausbildung", "1. Grundlagen"])!;

    expect(level.title).toBe("1. Grundlagen");
    // One floor down, not everything underneath.
    expect(level.children.map((c) => c.title)).toEqual(["3. Signal"]);
    expect(level.items).toHaveLength(0);
  });

  test("a folder holding lessons beside a folder shows both", () => {
    const level = divisionAt(course().divisions, ["Ausbildung", "1. Grundlagen", "3. Signal"])!;

    expect(level.items.map((i) => i.title)).toEqual(["A", "B"]);
    expect(level.children.map((c) => c.title)).toEqual(["14. Exkurs"]);
  });

  test("a folder that is not there is null, not an empty level", () => {
    expect(divisionAt(course().divisions, ["Nope"])).toBeNull();
    expect(divisionAt(course().divisions, ["Ausbildung", "Nope"])).toBeNull();
    // A real folder reached through one that is not.
    expect(divisionAt(course().divisions, ["Nope", "1. Grundlagen"])).toBeNull();
  });

  test("a folder counts every lesson beneath it, not just its own", () => {
    const divisions = course().divisions;

    expect(lessonsUnder(divisionAt(divisions, ["Ausbildung"])!)).toBe(4);
    expect(lessonsUnder(divisionAt(divisions, ["Ausbildung", "1. Grundlagen"])!)).toBe(3);
    // Two of its own, plus the one in the folder below it.
    expect(lessonsUnder(divisionAt(divisions, ["Ausbildung", "1. Grundlagen", "3. Signal"])!)).toBe(3);
    expect(lessonsUnder(divisionAt(divisions, ["Basis"])!)).toBe(1);
  });

  test("the stand-in counts the whole course, as the shelf card does", () => {
    const collection = course();
    expect(lessonsUnder(divisionAt(collection.divisions, [])!)).toBe(collection.count);
  });
});

describe("the order a level reads in", () => {
  /** The real shape: lessons 1..3 with 2 missing, and a folder called "2.". */
  const level = () => ({
    items: [
      set({ kind: "tut", episode: "1", title: "Definition" }),
      set({ kind: "tut", episode: "3", title: "Umsetzung" }),
    ],
    children: [{ title: "2. Exkurs", season: null, items: [], children: [] }],
  });

  test("a numbered folder sits where its number puts it, not at the end", () => {
    expect(levelEntries(level()).map((e) => (e.kind === "lesson" ? e.set.title : e.division.title)))
      .toEqual(["Definition", "2. Exkurs", "Umsetzung"]);
  });

  test("what is unnumbered goes last, lessons before folders", () => {
    const entries = levelEntries({
      items: [set({ kind: "tut", episode: null, title: "Intro" })],
      children: [
        { title: "Anhang", season: null, items: [], children: [] },
        { title: "1. Grundlagen", season: null, items: [], children: [] },
      ],
    });

    expect(entries.map((e) => (e.kind === "lesson" ? e.set.title : e.division.title))).toEqual([
      "1. Grundlagen",
      "Intro",
      "Anhang",
    ]);
  });

  test("a level of only lessons is left exactly as it came", () => {
    const entries = levelEntries({
      items: [
        set({ kind: "tut", episode: "1", title: "One" }),
        set({ kind: "tut", episode: "2", title: "Two" }),
      ],
      children: [],
    });

    expect(entries.every((e) => e.kind === "lesson")).toBe(true);
    expect(entries.map((e) => (e.kind === "lesson" ? e.set.title : null))).toEqual(["One", "Two"]);
  });
});

describe("what comes next", () => {
  // Built once and shared: `set()` mints a random id each call, so rebuilding
  // between the lookup and the question asks about a different library.
  const show = groupLibrary([
    set({ kind: "ep", show: "Star City", season: 1, episode: "1", title: "S1E1" }),
    set({ kind: "ep", show: "Star City", season: 1, episode: "2", title: "S1E2" }),
    set({ kind: "ep", show: "Star City", season: 2, episode: "1", title: "S2E1" }),
  ]).series[0]!;

  test("the next episode of the season", () => {
    expect(nextAfter(show, show.divisions[0]!.items[0]!.setId)?.title).toBe("S1E2");
  });

  test("crosses into the next season without being told there was one", () => {
    expect(nextAfter(show, show.divisions[0]!.items[1]!.setId)?.title).toBe("S2E1");
  });

  test("the last episode of the last season has nothing after it", () => {
    expect(nextAfter(show, show.divisions[1]!.items[0]!.setId)).toBeNull();
  });

  test("a set that is not in this collection has no next", () => {
    expect(nextAfter(show, "01NOTHERE")).toBeNull();
  });

  test("a course walks out of a folder and on to the next one", () => {
    const course = groupLibrary([
      set({ kind: "tut", show: "Kurs", path: "A", episode: "1", title: "A1" }),
      set({ kind: "tut", show: "Kurs", path: "A/Tiefer", episode: "2", title: "A2" }),
      set({ kind: "tut", show: "Kurs", path: "B", episode: "1", title: "B1" }),
    ]).tutorials[0]!;

    const a1 = course.divisions[0]!.items[0]!;
    // Into the nested folder, then out of it and into the sibling.
    expect(nextAfter(course, a1.setId)?.title).toBe("A2");
    const a2 = course.divisions[0]!.children[0]!.items[0]!;
    expect(nextAfter(course, a2.setId)?.title).toBe("B1");
  });
});
