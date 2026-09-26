/**
 * Cast, people and franchises from a v9 index — and nothing, without error,
 * from an older one.
 */

import { describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";

import { createRouter } from "../src/routes";
import type { ByteSource } from "../src/http/stream";
import type { PlayerRequest } from "../src/http/contracts";
import { creditsFor, franchises, peopleSearch, personFor } from "../src/catalog/credits";
import { emptyIndex } from "./index-fixture";

function withCredits(db: Database) {
  db.run(`CREATE TABLE credits(source TEXT NOT NULL, kind TEXT NOT NULL, id INTEGER NOT NULL, ord INTEGER NOT NULL,
    person_id INTEGER NOT NULL, name TEXT NOT NULL, role TEXT, dept TEXT NOT NULL, profile TEXT,
    PRIMARY KEY(source, kind, id, ord))`);
  db.run("CREATE TABLE franchises(source TEXT NOT NULL, id INTEGER NOT NULL, name TEXT NOT NULL, overview TEXT, PRIMARY KEY(source, id))");
  const credit = db.prepare("INSERT INTO credits VALUES ('tmdb', ?1, ?2, ?3, ?4, ?5, ?6, ?7, NULL)");
  credit.run("movie", 199, 0, 2387, "Patrick Stewart", "Jean-Luc Picard", "cast");
  credit.run("movie", 199, 1, 1213786, "Brent Spiner", "Data", "cast");
  credit.run("movie", 199, 2, 2388, "Jonathan Frakes", "Director", "crew");
  credit.run("movie", 200, 0, 2387, "Patrick Stewart", "Jean-Luc Picard", "cast");
  credit.run("movie", 200, 1, 99, "Armin Müller-Stahl", "Doctor", "cast");
  db.run("INSERT INTO franchises VALUES ('tmdb', 115575, 'Star Trek: The Next Generation Collection', '  ')");
  return db;
}
const holds = (keys: string[]) => (key: string) => keys.includes(key);

describe("credits", () => {
  test("cast in billing order, crew apart, portraits only where the image is held", () => {
    const { cast, crew } = creditsFor(withCredits(emptyIndex()), "tmdb-movie-199", holds(["tmdb-person-2387"]));
    expect(cast.map((c) => [c.name, c.role, c.portrait])).toEqual([
      ["Patrick Stewart", "Jean-Luc Picard", "tmdb-person-2387"],
      ["Brent Spiner", "Data", null],
    ]);
    expect(crew.map((c) => [c.name, c.role])).toEqual([["Jonathan Frakes", "Director"]]);
  });

  test("a person is their titles as catalog keys, never titles the page cannot see", () => {
    const person = personFor(withCredits(emptyIndex()), 2387, holds([]))!;
    expect(person).toEqual({ personId: 2387, name: "Patrick Stewart", portrait: null, titles: ["tmdb-movie-199", "tmdb-movie-200"] });
  });

  test("people are found by every word of the query, most-credited first", () => {
    const find = peopleSearch(withCredits(emptyIndex()), holds([]));
    expect(find("stewart").map((p) => [p.name, p.titles])).toEqual([["Patrick Stewart", ["tmdb-movie-199", "tmdb-movie-200"]]]);
    expect(find("patrick spiner")).toEqual([]);
    expect(find("mueller").map((p) => p.name)).toEqual(["Armin Müller-Stahl"]);
    expect(find("")).toEqual([]);
  });

  test("franchises, with an empty overview as none", () => {
    expect(franchises(withCredits(emptyIndex()))).toEqual([
      { id: 115575, name: "Star Trek: The Next Generation Collection", overview: null },
    ]);
  });

  test("an index from before v9 has nobody in it, and says so without failing", async () => {
    const db = emptyIndex();
    expect(creditsFor(db, "tmdb-movie-199", holds([]))).toEqual({ cast: [], crew: [] });
    expect(personFor(db, 2387, holds([]))).toBeNull();
    expect(franchises(db)).toEqual([]);
    const route = createRouter({ db, source: NO_BYTES });
    expect((await route(get("/api/people/2387"))).status).toBe(404);
    expect(JSON.parse(await new Response((await route(get("/api/franchises"))).body).text())).toEqual([]);
  });

  test("the routes answer from a v9 index", async () => {
    const route = createRouter({ db: withCredits(emptyIndex()), source: NO_BYTES });
    const body = async (path: string) => JSON.parse(await new Response((await route(get(path))).body).text());
    expect((await body("/api/shows/tmdb-movie-199/credits")).cast).toHaveLength(2);
    expect((await body("/api/people/2387")).titles).toEqual(["tmdb-movie-199", "tmdb-movie-200"]);
  });
});

const NO_BYTES: ByteSource = { stream: () => new ReadableStream({ start: (c) => c.close() }) };
const get = (path: string): PlayerRequest => ({ method: "GET", path, range: null });
