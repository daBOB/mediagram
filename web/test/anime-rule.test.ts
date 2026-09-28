/**
 * `isAnime` against the fixture shared with the Rust core's own test, and
 * `animeOverrides` against the table shapes an index can actually carry: no
 * table at all, `NULL` rows kept back to automatic, and both values held.
 */

import { Database } from "bun:sqlite";
import { expect, test } from "bun:test";
import { animeOverrides, isAnime } from "../src/catalog/anime";
import cases from "./fixtures/anime/cases.json";

for (const { name, kind, genres, originalLanguage, override, anime } of cases) {
  test(`isAnime: ${name}`, () => {
    expect(isAnime(kind, genres, originalLanguage, override)).toBe(anime);
  });
}

test("animeOverrides reads nothing from an index older than the table", () => {
  const db = new Database(":memory:");
  try {
    expect(animeOverrides(db).size).toBe(0);
  } finally { db.close(); }
});

test("animeOverrides drops NULL rows and keeps both true and false decisions", () => {
  const db = new Database(":memory:");
  try {
    db.run("CREATE TABLE anime_overrides(source TEXT, kind TEXT, id INTEGER, anime INTEGER, set_at INTEGER)");
    db.run("INSERT INTO anime_overrides VALUES ('tmdb', 'movie', 1, 1, 100)");
    db.run("INSERT INTO anime_overrides VALUES ('tmdb', 'movie', 2, 0, 100)");
    db.run("INSERT INTO anime_overrides VALUES ('tmdb', 'tv', 3, NULL, 100)");
    const overrides = animeOverrides(db);
    expect(overrides.get("tmdb-movie-1")).toBe(true);
    expect(overrides.get("tmdb-movie-2")).toBe(false);
    expect(overrides.has("tmdb-tv-3")).toBe(false);
    expect(overrides.size).toBe(2);
  } finally { db.close(); }
});
