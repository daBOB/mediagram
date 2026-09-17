/**
 * Turning a flat catalog into the three shelves a viewer expects.
 *
 * The index stores one row per set and says what kind it is; the shape a
 * library page needs — films, shows with seasons, courses with chapters — is
 * derived, and derived in one tested place rather than inside a render loop.
 */

import { describe, expect, test } from "bun:test";
import { groupLibrary, type CatalogSet } from "../public/lib/library.js";

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
