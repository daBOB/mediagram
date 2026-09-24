/**
 * The poster store, which is what a card's artwork comes from.
 *
 * It reads `posters/` beside whichever index the player opened: inside the
 * catalog a package unpacked, or next to a local library.db. The two are the
 * same code path on purpose — a local player used to have no artwork at all.
 */

import { Database } from "bun:sqlite";
import { describe, expect, test } from "bun:test";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, utimesSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { createRouter } from "../src/routes";
import type { ByteSource } from "../src/http/stream";
import type { PlayerRequest } from "../src/http/contracts";
import { PosterStore, posterKeyFor } from "../src/package/posters";
import { emptyIndex } from "./index-fixture";

/** A directory holding the given poster files, as either mode would have it. */
function withPosters(...names: string[]): string {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-posters-"));
  mkdirSync(join(dir, "posters"));
  for (const name of names) writeFileSync(join(dir, "posters", name), `bytes of ${name}`);
  return dir;
}

describe("a directory beside the index", () => {
  test("serves the posters it holds", () => {
    const store = new PosterStore(withPosters("tmdb-movie-693134.jpg", "tmdb-tv-1396.jpg"));

    expect(store.count()).toBe(2);
    expect(store.has("tmdb-movie-693134")).toBe(true);
    expect(new TextDecoder().decode(store.read("tmdb-tv-1396")!)).toBe("bytes of tmdb-tv-1396.jpg");
  });

  test("ignores anything that is not a poster", () => {
    const store = new PosterStore(withPosters("tmdb-movie-1.jpg", "notes.txt", "../escape.jpg"));

    expect(store.count()).toBe(1);
    expect(store.has("tmdb-movie-1")).toBe(true);
  });

  test("is empty, not broken, when there is no posters directory", () => {
    const dir = mkdtempSync(join(tmpdir(), "mediagram-bare-"));

    const store = new PosterStore(dir);

    expect(store.count()).toBe(0);
    expect(store.read("tmdb-movie-1")).toBeNull();
  });

  test("reads nothing at all when there is no directory to read", () => {
    const store = new PosterStore(null);

    expect(store.count()).toBe(0);
    expect(store.has("tmdb-movie-1")).toBe(false);
  });
});

describe("which sets can have artwork", () => {
  test("a film and a series are keyed apart on the same id", () => {
    expect(posterKeyFor("movie", 550)).toBe("tmdb-movie-550");
    expect(posterKeyFor("ep", 550)).toBe("tmdb-tv-550");
  });

  test("a course has no id to key a poster by", () => {
    expect(posterKeyFor("tut", null)).toBeNull();
  });

  test("an id that is not a positive integer keys nothing", () => {
    expect(posterKeyFor("movie", 0)).toBeNull();
    expect(posterKeyFor("movie", -1)).toBeNull();
    expect(posterKeyFor("movie", 1.5)).toBeNull();
  });
});

/** A set the player will offer, carrying the TMDB id its artwork is keyed by. */
function completeSet(db: Database, setId: string, kind: string, tmdb: number) {
  db.run(
    `INSERT INTO sets(set_id, kind, tmdb, title, show, container, vcodec, acodec,
                      duration, total, part_count, status, created_at, spec_version, season, episode, year)
     VALUES (?, ?, ?, 'Blade: Trinity', NULL, 'mkv', 'hevc', 'ac3', 5400, 1024, 1, 'complete', 1700000000, 3, NULL, NULL, 2004)`,
    [setId, kind, tmdb],
  );
  db.run(
    `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
     VALUES (?, 0, 0, 1024, -1001, 1, 2, ?, 'done')`,
    [setId, "a".repeat(64)],
  );
}

const NO_BYTES: ByteSource = {
  stream: () => new ReadableStream({ start: (c) => c.close() }),
};

function get(path: string): PlayerRequest {
  return { method: "GET", path, range: null };
}

/**
 * The point of the whole arrangement: a player reading a local index, with no
 * package anywhere, serves artwork to the browser.
 */
describe("serving artwork from a local index", () => {
  test("a film's card is given its poster, and the bytes are there to fetch", async () => {
    const dir = withPosters("tmdb-movie-36648.jpg");
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000001", "movie", 36648);
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(dir) });

    const catalog = await route(get("/api/sets"));
    const [set] = JSON.parse(await new Response(catalog.body).text());
    expect(set.poster).toBe("tmdb-movie-36648");

    const image = await route(get("/api/posters/tmdb-movie-36648.jpg"));
    expect(image.status).toBe(200);
    expect(image.headers["content-type"]).toBe("image/jpeg");
    expect(await new Response(image.body).text()).toBe("bytes of tmdb-movie-36648.jpg");
  });

  test("a course keeps its initials rather than borrowing someone's artwork", async () => {
    const dir = withPosters("tmdb-movie-36648.jpg");
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000002", "tut", 0);
    db.run("UPDATE sets SET tmdb = NULL WHERE set_id = '01SET0000000000000000002'");
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(dir) });

    const catalog = await route(get("/api/sets"));
    const [set] = JSON.parse(await new Response(catalog.body).text());
    expect(set.poster).toBeNull();
  });

  test("a title whose artwork was never fetched is a blank card, not a 500", async () => {
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000003", "movie", 999999);
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(withPosters()) });

    const catalog = await route(get("/api/sets"));
    const [set] = JSON.parse(await new Response(catalog.body).text());
    expect(set.poster).toBeNull();
    expect((await route(get("/api/posters/tmdb-movie-999999.jpg"))).status).toBe(404);
  });
});

describe("a season's own artwork", () => {
  test("an episode names its season's poster, which is served like any other", async () => {
    const dir = withPosters("tmdb-tv-1396.jpg", "tmdb-tv-1396-s2.jpg");
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000010", "ep", 1396);
    completeSet(db, "01SET0000000000000000011", "ep", 1396);
    db.run("UPDATE sets SET season = 2 WHERE set_id = '01SET0000000000000000010'");
    db.run("UPDATE sets SET season = 3 WHERE set_id = '01SET0000000000000000011'");
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(dir) });

    const catalog = await route(get("/api/sets"));
    const sets = JSON.parse(await new Response(catalog.body).text());
    const bySeason = new Map(sets.map((set: any) => [set.season, set]));
    expect((bySeason.get(2) as any).seasonPoster).toBe("tmdb-tv-1396-s2");
    // Season three has no artwork of its own; the page falls back to the show's.
    expect((bySeason.get(3) as any).seasonPoster).toBeNull();
    expect((bySeason.get(3) as any).poster).toBe("tmdb-tv-1396");

    expect((await route(get("/api/posters/tmdb-tv-1396-s2.jpg"))).status).toBe(200);
  });

  test("a film has no season poster", async () => {
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000012", "movie", 5);
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(withPosters()) });

    const [set] = JSON.parse(await new Response((await route(get("/api/sets"))).body).text());
    expect(set.seasonPoster).toBeNull();
  });
});

/**
 * Artwork can arrive while a player is running: `mediagram posters` writes
 * into the directory a local library is already being served from. Listing
 * the directory once made such a poster invisible until a restart.
 */
describe("artwork that arrives later", () => {
  /** Ages the directory so a same-millisecond write still counts as a change. */
  function age(dir: string) {
    const past = new Date(Date.now() - 10_000);
    utimesSync(join(dir, "posters"), past, past);
  }

  test("a poster added after the store was built is served", () => {
    const dir = withPosters("tmdb-movie-1.jpg");
    const store = new PosterStore(dir);
    expect(store.count()).toBe(1);

    age(dir);
    writeFileSync(join(dir, "posters", "tmdb-tv-252107.jpg"), "bytes of tmdb-tv-252107.jpg");

    expect(store.has("tmdb-tv-252107")).toBe(true);
    expect(store.count()).toBe(2);
    expect(new TextDecoder().decode(store.read("tmdb-tv-252107")!)).toBe(
      "bytes of tmdb-tv-252107.jpg",
    );
  });

  test("a poster removed after the store was built stops being offered", () => {
    const dir = withPosters("tmdb-movie-1.jpg", "tmdb-movie-2.jpg");
    const store = new PosterStore(dir);
    expect(store.count()).toBe(2);

    age(dir);
    rmSync(join(dir, "posters", "tmdb-movie-2.jpg"));

    expect(store.has("tmdb-movie-2")).toBe(false);
    expect(store.count()).toBe(1);
  });

  test("a directory that appears later is picked up", () => {
    const dir = mkdtempSync(join(tmpdir(), "mediagram-late-"));
    const store = new PosterStore(dir);
    expect(store.count()).toBe(0);

    mkdirSync(join(dir, "posters"));
    writeFileSync(join(dir, "posters", "tmdb-tv-1.jpg"), "art");

    expect(store.has("tmdb-tv-1")).toBe(true);
  });

});
