import { describe, expect, test } from "bun:test";
import { GAP, pageHash, pageLinks, pageOf, parsePage } from "../public/lib/catalog/pager.js";

const films = Array.from({ length: 100 }, (_, index) => index);

describe("pageOf", () => {
  test("cuts the requested page and reports where it sits", () => {
    expect(pageOf(films, 1, 48)).toEqual({ items: films.slice(0, 48), page: 1, pages: 3 });
    expect(pageOf(films, 2, 48).items).toEqual(films.slice(48, 96));
  });

  test("a partial last page holds only what is left", () => {
    expect(pageOf(films, 3, 48)).toEqual({ items: films.slice(96), page: 3, pages: 3 });
  });

  test("a page past the end lands on the last one", () => {
    expect(pageOf(films, 9, 48).page).toBe(3);
  });

  test("a page before the first, or not a whole number, lands on the first", () => {
    for (const page of [0, -2, Number.NaN, 1.5]) expect(pageOf(films, page, 48).page).toBe(1);
  });

  test("an empty shelf is one empty page", () => {
    expect(pageOf([], 4, 48)).toEqual({ items: [], page: 1, pages: 1 });
  });
});

describe("parsePage", () => {
  test("reads a positive whole number and nothing else", () => {
    expect(parsePage("5")).toBe(5);
    for (const text of [undefined, "", "0", "-1", "2.5", "x", "3a"]) expect(parsePage(text)).toBe(1);
  });
});

describe("pageHash", () => {
  test("page one is the plain shelf, so older links stay the same", () => {
    expect(pageHash("movies", 1)).toBe("#/movies");
    expect(pageHash("movies", 4)).toBe("#/movies/page/4");
  });
});

describe("pageLinks", () => {
  test("a single page needs no pager", () => {
    expect(pageLinks(1, 1)).toEqual([]);
  });

  test("few pages are all listed", () => {
    expect(pageLinks(3, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  test("many pages keep the ends and the neighbours, with gaps between", () => {
    expect(pageLinks(15, 30)).toEqual([1, GAP, 14, 15, 16, GAP, 30]);
    expect(pageLinks(1, 30)).toEqual([1, 2, GAP, 30]);
    expect(pageLinks(30, 30)).toEqual([1, GAP, 29, 30]);
  });

  test("a gap of a single page is written as that page", () => {
    expect(pageLinks(4, 30)).toEqual([1, 2, 3, 4, 5, GAP, 30]);
    expect(pageLinks(27, 30)).toEqual([1, GAP, 26, 27, 28, 29, 30]);
  });
});
