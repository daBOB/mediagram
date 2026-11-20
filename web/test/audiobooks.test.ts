/**
 * Audiobooks are a department of their own: a book never sits among films or
 * lessons, is browsed by genre, and is for every kid when it is a children's
 * book, whatever FSK it does not have.
 */

import { describe, expect, test } from "bun:test";
import { categoryRows } from "../public/lib/categories.js";
import { forKidsProfile, KIDS_BOOK_CATEGORY } from "../public/lib/age-rating.js";
import { transcodeBadge } from "../public/lib/catalog/set-badge.js";
import { groupDepartments } from "../public/lib/departments.js";
import { catalogSet } from "./support/catalog-set";

const book = (title: string, category: string | null = null) =>
  catalogSet({ setId: title, title, kind: "book", container: "mp3", vcodec: null, acodec: "mp3", category });

describe("groupDepartments", () => {
  test("files books under audiobooks only, by title", () => {
    const library = groupDepartments([
      catalogSet({ setId: "m", title: "Heat", kind: "movie" }),
      catalogSet({ setId: "d", title: "Planet", kind: "docu" }),
      book("B"), book("A"),
    ]);
    expect(library.audiobooks.map((set) => set.title)).toEqual(["A", "B"]);
    expect(library.movies.map((set) => set.title)).toEqual(["Heat"]);
    expect(library.documentaries.singles.map((set) => set.title)).toEqual(["Planet"]);
    expect(library.tutorials).toEqual([]);
  });
});

test("a book without a genre sits under Other, after the genres", () => {
  const rows = categoryRows([book("A", "Sachbuch"), book("B")], (set: { category: string | null }) => set.category);
  expect(rows.map((row: { title: string }) => row.title)).toEqual(["Sachbuch", "Other"]);
});

describe("a kids profile", () => {
  const kids = book("K", KIDS_BOOK_CATEGORY);
  const grown = book("S", "Sachbuch");
  const bare = book("N");

  test("is shown a Kinderbücher book at every limit, and no other book", () => {
    for (const limit of [6, 12]) {
      expect(forKidsProfile([kids, grown, bare], new Map(), limit).map((set: { title: string | null }) => set.title)).toEqual(["K"]);
    }
  });

  test("is shown another book once it is marked from an age at or under its limit", () => {
    const marks = new Map([["S", 6]]);
    expect(forKidsProfile([kids, grown, bare], marks, 6).map((set: { title: string | null }) => set.title)).toEqual(["K", "S"]);
  });

  test("only the exact genre name counts", () => {
    expect(forKidsProfile([book("X", "kinderbücher")], new Map(), 12)).toEqual([]);
  });
});

test("a book's card never says it needs converting, since its MP3 plays as it is", () => {
  expect(transcodeBadge(book("A"))).toBeNull();
});
