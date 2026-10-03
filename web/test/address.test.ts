/**
 * `address.js` owns the hash format both directions. Three things must hold:
 *
 * 1. Every address round-trips: `parse(href(a))` is `a` again, names and all —
 *    a bookmark a viewer already holds must keep opening the same page.
 * 2. `parse` accepts everything `drawRoute` used to parse by hand, including
 *    its quirks — an unrecognised section is treated as if it had been typed
 *    "movies", trailing segments and all, which is why an unknown section
 *    followed by `/page/N` still opens that page of Movies.
 * 3. `href` writes exactly the text bookmarks already hold: a round trip
 *    alone passes a format that encodes wrongly in both directions.
 * 4. `docs/web-player.md`'s address table is real: every row it lists parses
 *    to the page kind it claims, so the table cannot drift from the code.
 */

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, test } from "bun:test";
import { href, parse, sectionOf } from "../public/lib/address.js";
import type { Address } from "../public/lib/address.js";

// One name carrying the whole battery a real title, folder or query might
// hold: a slash, a space, a percent sign, a hash, a question mark, an
// umlaut and an emoji — everything `encodeURIComponent` has to survive.
const WEIRD = "Foo/Bar 100% #tag? Ünïcode 🎬";
const WEIRD_FOLDER = "Season/Öäü 🎉 50%?";

describe("round trip: parse(href(a)) is a again", () => {
  const cases: [string, Address][] = [
    ["home", { page: "home" }],
    ["movies department", { page: "department", section: "movies" }],
    ["movies page N", { page: "moviesPage", n: 7 }],
    ["series department", { page: "department", section: "series" }],
    ["tutorials department", { page: "department", section: "tutorials" }],
    ["documentaries department", { page: "department", section: "documentaries" }],
    ["anime department", { page: "department", section: "anime" }],
    ["a show by name", { page: "show", section: "series", name: WEIRD, folders: [] }],
    ["an anime show by name", { page: "show", section: "anime", name: WEIRD, folders: [] }],
    ["a course by name", { page: "show", section: "tutorials", name: WEIRD, folders: [] }],
    ["a documentary collection by name", { page: "show", section: "documentaries", name: WEIRD, folders: [] }],
    ["a series season", { page: "show", section: "series", name: WEIRD, folders: [WEIRD_FOLDER] }],
    ["a course's nested folder trail", {
      page: "show", section: "tutorials", name: WEIRD,
      folders: [WEIRD_FOLDER, "Chapter 3", "Ünïcode/slash 🎬"],
    }],
    ["film by setId", { page: "film", setId: WEIRD }],
    ["genre by name", { page: "genre", name: WEIRD }],
    ["genres", { page: "genres" }],
    ["latest", { page: "latest" }],
    // Not encoded, matching cast.js's `#/person/${personId}`: a TMDB id is
    // plain digits, so there is nothing here for encodeURIComponent to do.
    ["person by id", { page: "person", id: "58217" }],
    ["search by query", { page: "search", query: WEIRD }],
    ["collections", { page: "collections" }],
    ["a list by UUID", { page: "list", id: "3fa85f64-5717-4562-b3fc-2c963f66afa6" }],
    ["a list whose id holds the same battery of characters", { page: "list", id: WEIRD }],
    ["a franchise", { page: "franchise", id: "42" }],
    ["continue", { page: "continue" }],
    ["watchlist", { page: "watchlist" }],
    ["settings", { page: "settings" }],
    ["stats", { page: "stats" }],
    ["system", { page: "system" }],
  ];

  test.each(cases)("%s", (_label, address) => {
    expect(parse(href(address))).toEqual(address);
  });
});

describe("href writes exactly the text a bookmark holds", () => {
  test.each([
    [{ page: "film", setId: "Ü 100%/x" }, "#/film/%C3%9C%20100%25%2Fx"],
    [{ page: "search", query: "space adventure" }, "#/search/space%20adventure"],
    [{ page: "genre", name: "Sci-Fi & Fantasy" }, "#/genre/Sci-Fi%20%26%20Fantasy"],
    [{ page: "show", section: "tutorials", name: "Kurs Ü", folders: ["A/B", "C"] }, "#/tutorials/Kurs%20%C3%9C/A%2FB/C"],
    [{ page: "list", id: "a b" }, "#/collections/a%20b"],
    [{ page: "franchise", id: "42" }, "#/collections/tmdb-42"],
    [{ page: "person", id: "58217" }, "#/person/58217"],
    [{ page: "department", section: "series" }, "#/series"],
    // Always numbered: the plain `#/movies` is the department's front page.
    [{ page: "moviesPage", n: 1 }, "#/movies/page/1"],
    [{ page: "moviesPage", n: 4 }, "#/movies/page/4"],
    [{ page: "moviesPage", n: 0 }, "#/movies/page/1"],
  ] as [Address, string][])("%j -> %s", (address, text) => {
    expect(href(address)).toBe(text);
  });
});

describe("parse accepts what drawRoute used to parse by hand", () => {
  const cases: [string, Address][] = [
    ["", { page: "department", section: "movies" }],
    ["#", { page: "department", section: "movies" }],
    ["#/", { page: "department", section: "movies" }],
    ["#/movies", { page: "department", section: "movies" }],
    // An unrecognised section is treated exactly as "movies" — including
    // whatever followed it, which is how a typo'd section can still land on
    // a movies page rather than always falling back to its front page.
    ["#/bogus", { page: "department", section: "movies" }],
    ["#/bogus/page/5", { page: "moviesPage", n: 5 }],
    ["#/zzz/foo/bar", { page: "department", section: "movies" }],
    ["#/settingsxyz", { page: "department", section: "movies" }],
    ["#/movies/pagexyz", { page: "department", section: "movies" }],
    ["#/movies/page/xyz", { page: "moviesPage", n: 1 }],
    ["#/movies/page/1", { page: "moviesPage", n: 1 }],
    ["#/movies/page/5", { page: "moviesPage", n: 5 }],
    ...["0", "-1", "2.5", "x", "3a"].map((n): [string, Address] => [`#/movies/page/${n}`, { page: "moviesPage", n: 1 }]),
    // A person id and a franchise's `tmdb-` were never encoded, so neither is
    // decoded: an escaped `tmdb-` is a list id, not a franchise.
    ["#/person/a%20b", { page: "person", id: "a%20b" }],
    ["#/collections/tmdb%2D5", { page: "list", id: "tmdb-5" }],
    ["#/documentaries", { page: "department", section: "documentaries" }],
    ["#/documentaries/Foo", { page: "show", section: "documentaries", name: "Foo", folders: [] }],
    ["#/anime", { page: "department", section: "anime" }],
    ["#/anime/Foo", { page: "show", section: "anime", name: "Foo", folders: [] }],
    ["#/series", { page: "department", section: "series" }],
    ["#/series/Foo", { page: "show", section: "series", name: "Foo", folders: [] }],
    ["#/series/A%2FB/Season%202", { page: "show", section: "series", name: "A/B", folders: ["Season 2"] }],
    ["#/tutorials", { page: "department", section: "tutorials" }],
    ["#/tutorials/Course/Basics/Advanced", {
      page: "show", section: "tutorials", name: "Course", folders: ["Basics", "Advanced"],
    }],
    ["#/collections", { page: "collections" }],
    ["#/collections/tmdb-1", { page: "franchise", id: "1" }],
    ["#/collections/abc", { page: "list", id: "abc" }],
    ["#/home", { page: "home" }],
    ["#/film/abc", { page: "film", setId: "abc" }],
    ["#/genre/Sci-Fi", { page: "genre", name: "Sci-Fi" }],
    ["#/genres", { page: "genres" }],
    ["#/latest", { page: "latest" }],
    ["#/person/9", { page: "person", id: "9" }],
    ["#/search/space", { page: "search", query: "space" }],
    ["#/settings", { page: "settings" }],
    ["#/stats", { page: "stats" }],
    ["#/statsxyz", { page: "department", section: "movies" }],
    ["#/system", { page: "system" }],
    ["#/continue", { page: "continue" }],
    ["#/watchlist", { page: "watchlist" }],
  ];

  test.each(cases)("%s", (hash, expected) => {
    expect(parse(hash)).toEqual(expected);
  });
});

describe("sectionOf: the nav item and body[data-page] an address lights up", () => {
  test.each([
    ["#/bogus/page/5", "movies"],
    ["#/collections/tmdb-1", "collections"],
    ["#/collections/abc", "collections"],
    ["#/series/Foo", "series"],
    ["#/documentaries", "documentaries"],
    ["#/anime", "anime"],
    ["#/anime/Foo", "anime"],
    ["#/film/abc", "film"],
    ["#/stats", "stats"],
  ])("%s -> %s", (hash, section) => {
    expect(sectionOf(parse(hash))).toBe(section);
  });
});

describe("docs/web-player.md's address table", () => {
  const doc = readFileSync(join(import.meta.dir, "../../docs/web-player.md"), "utf8");
  const rows = [...doc.matchAll(/^\| `(#\/[^`]*)` \|/gm)].map((m) => m[1]!);

  // The page kind each documented template names. The hash tested is made
  // from the row itself — each `<placeholder>` becomes its bare word, `N`
  // becomes 2, a trailing `/...` one more folder — so a row edited wrongly
  // fails, and a row added without an entry here fails loudly.
  const kinds: Record<string, Address["page"]> = {
    "#/home": "home",
    "#/movies": "department",
    "#/movies/page/N": "moviesPage",
    "#/series": "department",
    "#/series/<show>": "show",
    "#/series/<show>/<season>": "show",
    "#/tutorials": "department",
    "#/tutorials/<course>": "show",
    "#/tutorials/<course>/<folder>/...": "show",
    "#/documentaries": "department",
    "#/documentaries/<collection>/<folder>/...": "show",
    "#/anime": "department",
    "#/anime/<show>": "show",
    "#/collections": "collections",
    "#/collections/tmdb-<id>": "franchise",
    "#/collections/<list-id>": "list",
    "#/film/<setId>": "film",
    "#/genre/<name>": "genre",
    "#/genres": "genres",
    "#/latest": "latest",
    "#/person/<id>": "person",
    "#/search/<query>": "search",
    "#/settings": "settings",
    "#/system": "system",
    "#/continue": "continue",
    "#/watchlist": "watchlist",
  };
  const concrete = (template: string) =>
    template.replace(/<([^>]+)>/g, "$1").replace(/\/N$/, "/2").replace(/\/\.\.\.$/, "/more");

  test("lists exactly the templates this test knows how to check", () => {
    expect(rows.sort()).toEqual(Object.keys(kinds).sort());
  });

  test.each(rows)("%s parses to the page it documents, and back", (template) => {
    const hash = concrete(template);
    expect(parse(hash).page).toBe(kinds[template]!);
    expect(href(parse(hash))).toBe(hash);
  });
});
