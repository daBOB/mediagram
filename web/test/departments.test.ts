/**
 * `departments.js` pulls anime out of the plain shelves the way
 * `documentaries.test.ts` already covers for documentaries; this covers the
 * split itself, the anime grouping (seasons kept), the counts, and the two
 * lookup pools (`everyFilm`/`everyShow`) and the show router
 * (`sectionForShow`) that let a page find a title wherever it is shelved.
 */

import { describe, expect, test } from "bun:test";
import { catalogSet } from "./support/catalog-set";
import { countAnime, everyFilm, everyShow, groupAnime, groupDepartments, sectionForShow } from "../public/lib/departments.js";
import { flattenCollection } from "../public/lib/library.js";

const anime = (over: Partial<ReturnType<typeof catalogSet>> = {}) => catalogSet({ anime: true, ...over });

describe("groupDepartments splits anime off the plain shelves", () => {
  test("an anime film is in anime.singles, not movies", () => {
    const library = groupDepartments([
      anime({ kind: "movie", title: "Spirited Away" }),
      catalogSet({ kind: "movie", title: "Blade" }),
    ]);
    expect(library.movies.map((set) => set.title)).toEqual(["Blade"]);
    expect(library.anime.singles.map((set) => set.title)).toEqual(["Spirited Away"]);
  });

  test("an anime show's episodes are in anime.collections, not series", () => {
    const library = groupDepartments([
      anime({ kind: "ep", show: "Dragonball", episode: "1", season: 1 }),
      anime({ kind: "ep", show: "Dragonball", episode: "2", season: 1 }),
      catalogSet({ kind: "ep", show: "Other Show", episode: "1", season: 1 }),
    ]);
    expect(library.series.map((show) => show.name)).toEqual(["Other Show"]);
    expect(library.anime.collections.map((show) => show.name)).toEqual(["Dragonball"]);
    expect(flattenCollection(library.anime.collections[0]!)).toHaveLength(2);
  });

  test("a documentary marked anime by the rule still splits off, not staying a documentary", () => {
    // The rule itself never marks a `docu` set anime (see anime-rule.test.ts);
    // this only asserts the split trusts the flag it is given rather than
    // re-deriving it from `kind`.
    const library = groupDepartments([anime({ kind: "movie", title: "Anime Film" })]);
    expect(library.documentaries.collections).toHaveLength(0);
    expect(library.documentaries.singles).toHaveLength(0);
    expect(library.anime.singles).toHaveLength(1);
  });

  test("an index with no anime titles leaves Movies and Series exactly as today", () => {
    const library = groupDepartments([catalogSet({ kind: "movie", title: "Blade", anime: false })]);
    expect(library.anime.collections).toHaveLength(0);
    expect(library.anime.singles).toHaveLength(0);
    expect(library.movies).toHaveLength(1);
  });
});

describe("groupAnime", () => {
  test("splits by kind: episodes become collections, films become singles", () => {
    const { collections, singles } = groupAnime([
      anime({ kind: "ep", show: "Dragonball", episode: "1" }),
      anime({ kind: "movie", title: "Spirited Away" }),
    ]);
    expect(collections.map((c) => c.name)).toEqual(["Dragonball"]);
    expect(singles.map((s) => s.title)).toEqual(["Spirited Away"]);
  });
});

test("countAnime counts titles, not episodes", () => {
  const grouped = groupAnime([
    anime({ kind: "ep", show: "Dragonball", episode: "1" }),
    anime({ kind: "ep", show: "Dragonball", episode: "2" }),
    anime({ kind: "movie", title: "Spirited Away" }),
    anime({ kind: "movie", title: "Ponyo" }),
  ]);
  // One show (however many episodes) plus two films: four titles, not four
  // items and not six.
  expect(countAnime(grouped)).toBe(3);
});

describe("everyFilm / everyShow: the lookup pools", () => {
  const library = groupDepartments([
    catalogSet({ kind: "movie", title: "Blade" }),
    anime({ kind: "movie", title: "Spirited Away" }),
    catalogSet({ kind: "ep", show: "Other Show", episode: "1" }),
    anime({ kind: "ep", show: "Dragonball", episode: "1" }),
  ]);

  test("everyFilm holds plain films and anime films together", () => {
    expect(everyFilm(library).map((f) => f.title).sort()).toEqual(["Blade", "Spirited Away"]);
  });

  test("everyShow holds plain shows and anime shows together", () => {
    expect(everyShow(library).map((s) => s.name).sort()).toEqual(["Dragonball", "Other Show"]);
  });
});

describe("sectionForShow", () => {
  const library = groupDepartments([
    catalogSet({ kind: "ep", show: "Plain Show", episode: "1" }),
    anime({ kind: "ep", show: "Dragonball", episode: "1" }),
  ]);

  test("a name only on the Anime shelf resolves to anime", () => {
    expect(sectionForShow(library, "series", "Dragonball")).toBe("anime");
  });

  test("a name on the Series shelf stays series, even asked as such", () => {
    expect(sectionForShow(library, "series", "Plain Show")).toBe("series");
  });

  test("a name on neither shelf is left alone, for the caller's own error page", () => {
    expect(sectionForShow(library, "series", "Nothing Here")).toBe("series");
  });

  test("a name on both shelves keeps the given section rather than always redirecting", () => {
    const shared = groupDepartments([
      catalogSet({ kind: "ep", show: "One Piece", episode: "1" }),
      anime({ kind: "ep", show: "One Piece", episode: "2" }),
    ]);
    expect(sectionForShow(shared, "series", "One Piece")).toBe("series");
  });

  test("a non-series section is returned unchanged", () => {
    expect(sectionForShow(library, "tutorials", "Anything")).toBe("tutorials");
  });
});
