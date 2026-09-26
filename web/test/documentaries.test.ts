/**
 * Documentaries split two ways: a folder such as "Terra X" groups by `show`
 * exactly as a course does, and a documentary uploaded on its own — no
 * `show` — stays a single rather than being merged into one fake collection
 * with the others.
 */

import { describe, expect, test } from "bun:test";
import { catalogSet } from "./support/catalog-set";
import { countDocumentaries, groupDocumentaries } from "../public/lib/documentaries.js";
import { divisionAt, groupLibrary, flattenCollection } from "../public/lib/library.js";
import { extentOf } from "../public/lib/catalog/course-view.js";

const docu = (over: Partial<ReturnType<typeof catalogSet>> = {}) => catalogSet({ kind: "docu", ...over });

describe("splitting documentaries", () => {
  test("a folder becomes a collection, grouped by its own name", () => {
    const { collections, singles } = groupDocumentaries([
      docu({ show: "Terra X", title: "Episode 1", episode: "1" }),
      docu({ show: "Terra X", title: "Episode 2", episode: "2" }),
    ]);

    expect(singles).toHaveLength(0);
    expect(collections).toHaveLength(1);
    expect(collections[0]!.name).toBe("Terra X");
    expect(flattenCollection(collections[0]!)).toHaveLength(2);
  });

  test("a documentary with no folder is a single, not a shared collection", () => {
    const { collections, singles } = groupDocumentaries([
      docu({ title: "Free Solo", show: null }),
      docu({ title: "Citizenfour", show: null }),
    ]);

    expect(collections).toHaveLength(0);
    expect(singles.map((set) => set.title)).toEqual(["Citizenfour", "Free Solo"]);
  });

  test("counts every documentary, collected and single alike", () => {
    const library = groupDocumentaries([
      docu({ show: "Terra X", title: "Episode 1", episode: "1" }),
      docu({ show: "Terra X", title: "Episode 2", episode: "2" }),
      docu({ title: "Free Solo", show: null }),
    ]);

    expect(countDocumentaries(library)).toBe(3);
  });

  test("an empty library counts nothing", () => {
    expect(countDocumentaries(groupDocumentaries([]))).toBe(0);
  });
});

describe("groupLibrary keeps documentaries off the film shelf", () => {
  test("a `docu` set never lands among the movies", () => {
    const library = groupLibrary([docu({ title: "Free Solo" }), catalogSet({ kind: "movie", title: "Blade" })]);

    expect(library.movies).toHaveLength(1);
    expect(library.movies[0]!.title).toBe("Blade");
  });
});

test("a documentary collection counts documentaries, not lessons", () => {
  const { collections } = groupDocumentaries([
    docu({ show: "Terra X", title: "Episode 1", episode: "1" }),
    docu({ show: "Terra X", title: "Episode 2", episode: "2" }),
  ]);
  expect(extentOf(divisionAt(collections[0]!.divisions, []))).toBe("two documentaries");
});
