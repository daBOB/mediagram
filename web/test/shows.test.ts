/**
 * What the provider says about a show, and what happens when it has not.
 *
 * Every one of the absent cases is ordinary: an index written before the
 * table existed, a title nobody described, a key that names nothing. None of
 * them is an error, and treating any of them as one would break a library
 * that worked yesterday.
 */

import { Database } from "bun:sqlite";
import { describe, expect, test } from "bun:test";

import { createRouter } from "../src/routes";
import type { ByteSource } from "../src/http/stream";
import type { PlayerRequest } from "../src/http/contracts";
import { showMeta } from "../src/catalog/shows";
import { emptyIndex } from "./index-fixture";

const SHOWS = `CREATE TABLE shows(
    source TEXT NOT NULL, kind TEXT NOT NULL, id INTEGER NOT NULL,
    lang TEXT NOT NULL DEFAULT '',
    overview TEXT, tagline TEXT, genres TEXT, rating REAL,
    network TEXT, status TEXT, first_air TEXT, last_air TEXT,
    total_seasons INTEGER, total_episodes INTEGER,
    PRIMARY KEY(source, kind, id))`;

function described(db: Database, kind: string, id: number, over: Record<string, unknown> = {}) {
  db.run(SHOWS);
  const row = {
    overview: "A detective returns to the town that raised her.",
    tagline: null,
    genres: "Drama, Thriller",
    rating: 7.8,
    network: "Apple TV",
    status: "Returning Series",
    totalSeasons: 2,
    totalEpisodes: 16,
    ...over,
  };
  db.run(
    `INSERT INTO shows(source, kind, id, lang, overview, tagline, genres, rating,
                       network, status, total_seasons, total_episodes)
     VALUES ('tmdb', ?, ?, 'de-DE', ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      kind, id, row.overview, row.tagline, row.genres, row.rating,
      row.network, row.status, row.totalSeasons, row.totalEpisodes,
    ],
  );
}

const NO_BYTES: ByteSource = { stream: () => new ReadableStream({ start: (c) => c.close() }) };
const get = (path: string): PlayerRequest => ({ method: "GET", path, range: null });

describe("reading a show's description", () => {
  test("a described show comes back whole", () => {
    const db = emptyIndex();
    described(db, "tv", 252107);

    const meta = showMeta(db, "tmdb-tv-252107")!;

    expect(meta.genres).toBe("Drama, Thriller");
    expect(meta.rating).toBe(7.8);
    expect(meta.overview).toContain("detective");
    expect(meta.totalSeasons).toBe(2);
    expect(meta.totalEpisodes).toBe(16);
  });

  test("an index with no shows table is no description, not a crash", () => {
    expect(showMeta(emptyIndex(), "tmdb-tv-252107")).toBeNull();
  });

  test("a show nobody described is null", () => {
    const db = emptyIndex();
    described(db, "tv", 252107);

    expect(showMeta(db, "tmdb-tv-999999")).toBeNull();
  });

  test("a film and a series with the same id are different shows", () => {
    const db = emptyIndex();
    described(db, "tv", 550, { genres: "Drama" });

    expect(showMeta(db, "tmdb-tv-550")?.genres).toBe("Drama");
    expect(showMeta(db, "tmdb-movie-550")).toBeNull();
  });

  test("a key that is not a key names nothing", () => {
    const db = emptyIndex();
    described(db, "tv", 1);

    for (const bad of ["../../etc/passwd", "tmdb-tv-", "imdb-tv-1", "tmdb-show-1", ""]) {
      expect(showMeta(db, bad)).toBeNull();
    }
  });
});

describe("the endpoint", () => {
  test("serves a description as JSON", async () => {
    const db = emptyIndex();
    described(db, "tv", 252107);
    const route = createRouter({ db, source: NO_BYTES });

    const res = await route(get("/api/shows/tmdb-tv-252107"));

    expect(res.status).toBe(200);
    expect(res.headers["content-type"]).toBe("application/json");
    expect(JSON.parse(await new Response(res.body).text()).network).toBe("Apple TV");
  });

  test("a show with no description is a 404, which the page treats as none", async () => {
    const db = emptyIndex();
    described(db, "tv", 1);
    const route = createRouter({ db, source: NO_BYTES });

    expect((await route(get("/api/shows/tmdb-tv-777"))).status).toBe(404);
  });

  test("a malformed key is refused rather than reaching the query", async () => {
    const route = createRouter({ db: emptyIndex(), source: NO_BYTES });

    expect((await route(get("/api/shows/not-a-key"))).status).toBe(404);
  });
});
