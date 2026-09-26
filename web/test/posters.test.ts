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
import { PosterStore, backdropKeyFor, posterKeyFor, posterKeyIsValid } from "../src/package/posters";
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

  test("a course has no id to key a poster by, and no title to fall back to either", () => {
    expect(posterKeyFor("tut", null)).toBeNull();
  });

  test("an id that is not a positive integer keys nothing, without a fallback title", () => {
    expect(posterKeyFor("movie", 0)).toBeNull();
    expect(posterKeyFor("movie", -1)).toBeNull();
    expect(posterKeyFor("movie", 1.5)).toBeNull();
  });

  test("without a provider id, a title's own name keys its artwork instead", () => {
    expect(posterKeyFor("tut", null, "Geldhochschule")).toBe("title-geldhochschule");
    expect(posterKeyFor("docu", null, "Terra X")).toBe("title-terra-x");
  });

  test("a provider id wins over a fallback title when both are given", () => {
    expect(posterKeyFor("movie", 550, "Fight Club")).toBe("tmdb-movie-550");
  });

  test("a title that slugs to nothing keys nothing", () => {
    expect(posterKeyFor("tut", null, "日本語")).toBeNull();
    expect(posterKeyFor("tut", null, "")).toBeNull();
  });

  test("a title key is valid with and without a backdrop suffix, and nothing looser", () => {
    expect(posterKeyIsValid("title-geldhochschule")).toBe(true);
    expect(posterKeyIsValid("title-geldhochschule-bg")).toBe(true);
    for (const bad of ["title-", "title-Geldhochschule", "title--x", "title-x-", "title-x_y"]) {
      expect(posterKeyIsValid(bad)).toBe(false);
    }
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

describe("a title's backdrop", () => {
  test("a film names its backdrop, which is served like a poster", async () => {
    const dir = withPosters("tmdb-movie-5.jpg", "tmdb-movie-5-bg.jpg");
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000020", "movie", 5);
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(dir) });

    const [set] = JSON.parse(await new Response((await route(get("/api/sets"))).body).text());
    expect(set.backdrop).toBe("tmdb-movie-5-bg");
    expect((await route(get("/api/posters/tmdb-movie-5-bg.jpg"))).status).toBe(200);
  });

  test("a title with no backdrop fetched names none", async () => {
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000021", "ep", 7);
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(withPosters("tmdb-tv-7.jpg")) });

    const [set] = JSON.parse(await new Response((await route(get("/api/sets"))).body).text());
    expect(set.backdrop).toBeNull();
    expect(set.poster).toBe("tmdb-tv-7");
  });

  test("backdrops beside the posters are not counted as posters", () => {
    expect(new PosterStore(withPosters("tmdb-movie-5.jpg", "tmdb-movie-5-bg.jpg")).count()).toBe(1);
    expect(new PosterStore(withPosters("tmdb-movie-5.jpg", "tmdb-person-2387.jpg")).count()).toBe(1);
  });

  test("backdrop keys follow the uploader's rule and nothing looser", () => {
    expect(backdropKeyFor("tmdb-movie-5")).toBe("tmdb-movie-5-bg");
    expect(backdropKeyFor(null)).toBeNull();
    expect(posterKeyIsValid("tmdb-tv-7-bg")).toBe(true);
    expect(posterKeyIsValid("tmdb-person-2387")).toBe(true);
    for (const bad of ["tmdb-tv-7-s2-bg", "tmdb-tv-7-bg-s2", "tmdb-tv-7-BG", "tmdb-tv-7-bgx"]) {
      expect(posterKeyIsValid(bad)).toBe(false);
    }
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

/** A set with no provider id, keyed from its own name instead. */
function titledSet(db: Database, setId: string, kind: string, title: string, show: string | null = null) {
  db.run(
    `INSERT INTO sets(set_id, kind, tmdb, title, show, container, vcodec, acodec,
                      duration, total, part_count, status, created_at, spec_version, season, episode, year)
     VALUES (?, ?, NULL, ?, ?, 'mp4', 'h264', 'aac', 1200, 2048, 1, 'complete', 1700000000, 3, NULL, NULL, NULL)`,
    [setId, kind, title, show],
  );
  db.run(
    `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
     VALUES (?, 0, 0, 2048, -1001, 1, 2, ?, 'done')`,
    [setId, "b".repeat(64)],
  );
}

/**
 * `mediagram artwork` / `add-docu --poster` writes here — a table riding the
 * v10 push, so it exists on any index new enough to carry it.
 */
function withArtwork(db: Database, key: string, mime: string, bytes: string) {
  db.run("CREATE TABLE IF NOT EXISTS artwork(key TEXT PRIMARY KEY, mime TEXT NOT NULL, bytes BLOB NOT NULL)");
  db.run("INSERT INTO artwork(key, mime, bytes) VALUES (?, ?, ?)", [key, mime, Buffer.from(bytes)]);
}

describe("custom artwork, from the `artwork` table", () => {
  test("a course with no provider id shows the poster its own name was given", async () => {
    const db = emptyIndex();
    titledSet(db, "01SET0000000000000000030", "tut", "Geldhochschule");
    withArtwork(db, "title-geldhochschule", "image/png", "the course poster");
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(null) });

    const [set] = JSON.parse(await new Response((await route(get("/api/sets"))).body).text());
    expect(set.poster).toBe("title-geldhochschule");

    const image = await route(get("/api/posters/title-geldhochschule.jpg"));
    expect(image.status).toBe(200);
    expect(image.headers["content-type"]).toBe("image/png");
    expect(await new Response(image.body).text()).toBe("the course poster");
  });

  test("every lesson of a collection shares the one key its collection name gives", async () => {
    const db = emptyIndex();
    titledSet(db, "01SET0000000000000000031", "docu", "Episode 1", "Terra X");
    titledSet(db, "01SET0000000000000000032", "docu", "Episode 2", "Terra X");
    withArtwork(db, "title-terra-x", "image/jpeg", "terra x poster");
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(null) });

    const sets = JSON.parse(await new Response((await route(get("/api/sets"))).body).text());
    expect(sets.map((set: any) => set.poster)).toEqual(["title-terra-x", "title-terra-x"]);
  });

  test("a row in the table overrides a packaged file under the same key", async () => {
    const dir = withPosters("tmdb-movie-550.jpg");
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000033", "movie", 550);
    withArtwork(db, "tmdb-movie-550", "image/webp", "the manual override");
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(dir) });

    const image = await route(get("/api/posters/tmdb-movie-550.jpg"));
    expect(image.headers["content-type"]).toBe("image/webp");
    expect(await new Response(image.body).text()).toBe("the manual override");
  });

  test("a mime type this build will not relay falls back to the packaged file", async () => {
    const dir = withPosters("tmdb-movie-551.jpg");
    const db = emptyIndex();
    completeSet(db, "01SET0000000000000000034", "movie", 551);
    withArtwork(db, "tmdb-movie-551", "text/html", "<script>not an image</script>");
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(dir) });

    const image = await route(get("/api/posters/tmdb-movie-551.jpg"));
    expect(image.headers["content-type"]).toBe("image/jpeg");
    expect(await new Response(image.body).text()).toBe("bytes of tmdb-movie-551.jpg");
  });

  test("an index written before the table existed is a blank card, not a 500", async () => {
    const db = emptyIndex();
    titledSet(db, "01SET0000000000000000035", "tut", "Geldhochschule");
    const route = createRouter({ db, source: NO_BYTES, posters: new PosterStore(null) });

    const [set] = JSON.parse(await new Response((await route(get("/api/sets"))).body).text());
    expect(set.poster).toBeNull();
    expect((await route(get("/api/posters/title-geldhochschule.jpg"))).status).toBe(404);
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
