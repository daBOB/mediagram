/**
 * Course documents in the library.
 *
 * A document is a set like any other — complete, streamable, listed by the
 * catalog — and the only thing that says not to play it is its kind. The
 * shelving rule that saves everything unrecognised by putting it with the
 * films is exactly wrong for this one kind, which is what these pin down.
 */

import { describe, expect, test } from "bun:test";
import {
  documentsUnder,
  firstItemOf,
  flattenCollection,
  groupLibrary,
  isDocument,
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

const lesson = (over: Record<string, unknown> = {}): CatalogSet =>
  set({ kind: "tut", show: "Kurs", ...over });

const document = (over: Record<string, unknown> = {}): CatalogSet =>
  set({
    kind: "doc",
    show: "Kurs",
    container: "pdf",
    vcodec: null,
    acodec: null,
    duration: null,
    ...over,
  });

describe("shelving", () => {
  /** The regression the player change exists to prevent. */
  test("a handout never lands on the film shelf", () => {
    const library = groupLibrary([document({ title: "Arbeitsbuch", path: "Ressourcen" })]);

    expect(library.movies).toEqual([]);
    expect(library.tutorials).toHaveLength(1);
    expect(library.tutorials[0]!.name).toBe("Kurs");
  });

  test("a handout joins the course its lesson is in", () => {
    const library = groupLibrary([
      lesson({ title: "Signal", path: "Kapitel", episode: "2" }),
      document({ title: "Signal", path: "Kapitel", episode: "2" }),
    ]);

    const [course] = library.tutorials;
    expect(course!.divisions[0]!.title).toBe("Kapitel");
    expect(course!.divisions[0]!.items).toHaveLength(2);
  });

  /** "12 lessons" must mean twelve lessons. */
  test("a shelf card counts lessons and documents apart", () => {
    const library = groupLibrary([
      lesson({ title: "Eins", path: "Kapitel", episode: "1" }),
      document({ title: "Eins", path: "Kapitel", episode: "1" }),
      document({ title: "Arbeitsbuch", path: "Ressourcen", episode: "1" }),
    ]);

    const [course] = library.tutorials;
    expect(course!.count).toBe(1);
    expect(course!.documents).toBe(2);
    // The folder holding nothing but workbooks is structure, not a chapter.
    expect(course!.chapters).toBe(1);
  });

  test("an unknown kind is still shelved with the films", () => {
    const library = groupLibrary([set({ kind: "something-new", title: "Odd" })]);
    expect(library.movies).toHaveLength(1);
  });
});

describe("a document is not in the playback order", () => {
  const library = () =>
    groupLibrary([
      lesson({ setId: "L1", title: "Eins", path: "Kapitel", episode: "1" }),
      document({ setId: "D1", title: "Eins", path: "Kapitel", episode: "1" }),
      lesson({ setId: "L2", title: "Zwei", path: "Kapitel", episode: "2" }),
    ]);

  /** Reaching the end of a lesson must not auto-advance into a workbook. */
  test("what follows a lesson is the next lesson", () => {
    const course = library().tutorials[0]!;
    expect(nextAfter(course, "L1")?.setId).toBe("L2");
  });

  test("flattening a collection yields only what plays", () => {
    const course = library().tutorials[0]!;
    expect(flattenCollection(course).map((s) => s.setId)).toEqual(["L1", "L2"]);
  });

  /** A course whose first folder is its workbooks must not open a PDF. */
  test("the first item of a collection is playable", () => {
    const course = groupLibrary([
      document({ setId: "D1", title: "Arbeitsbuch", path: "A Ressourcen", episode: "1" }),
      lesson({ setId: "L1", title: "Eins", path: "B Kapitel", episode: "1" }),
    ]).tutorials[0]!;

    expect(firstItemOf(course.divisions)?.setId).toBe("L1");
  });
});

describe("a level's entries", () => {
  test("a document is marked as one, and sits at its own number", () => {
    const course = groupLibrary([
      lesson({ title: "Eins", path: "Kapitel", episode: "1" }),
      lesson({ title: "Zwei", path: "Kapitel", episode: "2" }),
      document({ title: "Zwei", path: "Kapitel", episode: "2" }),
    ]).tutorials[0]!;

    const entries = levelEntries(course.divisions[0]!);
    expect(entries.map((e) => e.kind)).toEqual(["lesson", "lesson", "document"]);
  });

  test("counting under a division separates the two", () => {
    const course = groupLibrary([
      lesson({ title: "Eins", path: "Kapitel", episode: "1" }),
      document({ title: "Eins", path: "Kapitel/Anhang", episode: "1" }),
      document({ title: "Zwei", path: "Kapitel/Anhang", episode: "2" }),
    ]).tutorials[0]!;

    expect(lessonsUnder(course.divisions[0]!)).toBe(1);
    expect(documentsUnder(course.divisions[0]!)).toBe(2);
  });
});

test("isDocument goes by kind, never by container", () => {
  expect(isDocument(document())).toBe(true);
  expect(isDocument(lesson({ container: "pdf" }))).toBe(false);
});

describe("a document is served as what it is", () => {
  test("a PDF gets the PDF media type, so a browser opens it", async () => {
    const { contentType } = await import("../src/response");
    expect(contentType("pdf")).toBe("application/pdf");
  });

  /** Nothing else changed: the video types still answer as before. */
  test("video containers are untouched", async () => {
    const { contentType } = await import("../src/response");
    expect(contentType("mkv")).toBe("video/x-matroska");
    expect(contentType("mp4")).toBe("video/mp4");
    expect(contentType("epub")).toBe("application/octet-stream");
  });
});
