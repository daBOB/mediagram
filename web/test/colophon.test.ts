import { describe, expect, test } from "bun:test";
import { catalogueAge, colophonLine } from "../public/lib/colophon.js";

const film = (over: Record<string, unknown> = {}) => ({
  kind: "movie",
  duration: 7200,
  total: 10_000_000_000,
  ...over,
});

describe("the colophon", () => {
  test("counts each kind in the words the shelves use", () => {
    const sets = [film(), film(), film({ kind: "ep" }), film({ kind: "tut" })];
    const line = colophonLine(sets, null);
    expect(line).toContain("two films");
    expect(line).toContain("one episode");
    expect(line).toContain("one lesson");
  });

  test("shelves an unrecognised kind with the films, as groupLibrary does", () => {
    // Nothing may silently vanish from a total a viewer checks against what
    // they uploaded.
    expect(colophonLine([film({ kind: "something-new" })], null)).toContain("one film");
  });

  test("adds up runtime and size across the whole library", () => {
    const sets = [film({ duration: 3600 }), film({ duration: 3600 })];
    const line = colophonLine(sets, null);
    expect(line).toContain("two hours");
    expect(line).toContain("19 GB");
  });

  test("says where the catalogue came from", () => {
    expect(colophonLine([film()], { origin: "local", publishedAt: null, schema: 6 })).toContain(
      "read from this machine",
    );
  });

  test("says how old a published catalogue is, and which schema it is at", () => {
    const now = new Date("2026-09-19T00:00:00Z");
    const threeDaysAgo = now.getTime() - 3 * 86_400_000;
    const line = colophonLine([film()], { origin: "package", publishedAt: threeDaysAgo, schema: 6 }, now);
    expect(line).toContain("published 3 days ago");
    expect(line).toContain("schema 6");
  });

  test("is shorter, not broken, before the server has answered", () => {
    const line = colophonLine([film()], null);
    expect(line).not.toContain("published");
    expect(line).not.toContain("undefined");
    expect(line).toContain("one film");
  });

  test("says something rather than nothing about an empty library", () => {
    expect(colophonLine([], null)).toBe("Nothing in the library yet");
  });

  test("leaves out a count of zero rather than printing it", () => {
    expect(colophonLine([film()], null)).not.toContain("zero");
  });
});

describe("how old a catalogue is", () => {
  const now = Date.parse("2026-09-19T12:00:00Z");
  const ago = (days: number) => catalogueAge(now - days * 86_400_000, now);

  test("counts in days while days are the useful unit", () => {
    expect(ago(0)).toBe("published today");
    expect(ago(1)).toBe("published yesterday");
    expect(ago(5)).toBe("published 5 days ago");
  });

  test("coarsens once an exact figure stops meaning anything", () => {
    expect(ago(21)).toBe("published 3 weeks ago");
    expect(ago(90)).toBe("published 3 months ago");
  });

  test("does not report a package from the future, which is a clock disagreeing", () => {
    expect(catalogueAge(now + 86_400_000, now)).toBe("published just now");
  });

  test("has no answer for a local index, which nothing published", () => {
    expect(catalogueAge(null, now)).toBe(null);
  });
});
