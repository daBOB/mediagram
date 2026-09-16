/**
 * What the player may offer, and where a set's bytes live.
 *
 * The fixture builds its own tables rather than importing the uploader's
 * schema: the player only ever reads, so the columns it names are its whole
 * dependency on the layout. What must not drift is the *definition* of
 * playable, and that is pinned from the Rust side — see
 * `crates/mediagram/tests/shared_playable_sql.rs`, which fails if
 * `PLAYABLE_SQL` stops matching the copy in `src/catalog.ts`.
 */

import { Database } from "bun:sqlite";
import { describe, expect, test } from "bun:test";
import { listPlayable, partLocations, playableSet } from "../src/catalog";

/** The subset of the uploader's schema the player reads. */
function fixture(): Database {
  const db = new Database(":memory:");
  db.run(`CREATE TABLE sets(
      set_id TEXT PRIMARY KEY, kind TEXT NOT NULL,
      tmdb INTEGER, tvdb INTEGER, imdb TEXT,
      show TEXT, title TEXT, year INTEGER,
      season INTEGER, episode TEXT, abs INTEGER,
      quality TEXT, hdr TEXT, container TEXT NOT NULL,
      vcodec TEXT, acodec TEXT,
      alang TEXT NOT NULL DEFAULT '[]', slang TEXT NOT NULL DEFAULT '[]',
      duration INTEGER, variant TEXT, group_key TEXT,
      total INTEGER NOT NULL, part_count INTEGER NOT NULL,
      set_hash TEXT, status TEXT NOT NULL DEFAULT 'pending',
      created_at INTEGER NOT NULL, spec_version INTEGER NOT NULL, chap TEXT)`);
  db.run(`CREATE TABLE parts(
      set_id TEXT NOT NULL, idx INTEGER NOT NULL,
      byte_offset INTEGER NOT NULL, byte_length INTEGER NOT NULL,
      chat_id INTEGER, message_id INTEGER, doc_id INTEGER, sha256 TEXT,
      status TEXT NOT NULL DEFAULT 'pending', verified_at INTEGER,
      PRIMARY KEY(set_id, idx))`);
  return db;
}

interface Span {
  off: number;
  len: number;
}

/** A complete set whose parts are all done — what PLAYABLE_SQL accepts. */
function completeSet(db: Database, setId: string, spans: Span[], createdAt = 1_700_000_000) {
  const total = spans.reduce((n, s) => n + s.len, 0);
  db.run(
    `INSERT INTO sets(set_id, kind, title, show, container, vcodec, acodec,
                      duration, total, part_count, status, created_at, spec_version, season, episode, year)
     VALUES (?, 'movie', 'The Matrix', NULL, 'mkv', 'hevc', 'ac3', 8160, ?, ?, 'complete', ?, 3, NULL, NULL, 1999)`,
    [setId, total, spans.length, createdAt],
  );
  spans.forEach((span, idx) => {
    db.run(
      `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
       VALUES (?, ?, ?, ?, -1001, ?, ?, ?, 'done')`,
      [setId, idx, span.off, span.len, 100 + idx, 900 + idx, "a".repeat(64)],
    );
  });
}

describe("the catalog", () => {
  test("offers a complete set with what a player needs to decide", () => {
    const db = fixture();
    completeSet(db, "01SET0000000000000000001", [{ off: 0, len: 1000 }]);

    const listed = listPlayable(db);

    expect(listed).toHaveLength(1);
    expect(listed[0]!.title).toBe("The Matrix");
    expect(listed[0]!.container).toBe("mkv");
    expect(listed[0]!.vcodec).toBe("hevc");
    expect(listed[0]!.acodec).toBe("ac3");
    expect(listed[0]!.total).toBe(1000);
    expect(listed[0]!.duration).toBe(8160);
  });

  test("does not offer a pending set", () => {
    const db = fixture();
    db.run(
      `INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
       VALUES ('01SET0000000000000000002', 'movie', 'mkv', 1000, 1, 'pending', 1, 3)`,
    );
    db.run(
      `INSERT INTO parts(set_id, idx, byte_offset, byte_length, status)
       VALUES ('01SET0000000000000000002', 0, 0, 1000, 'pending')`,
    );

    expect(listPlayable(db)).toEqual([]);
  });

  test("does not offer a set whose parts do not sum to its total", () => {
    const db = fixture();
    // Marked complete, but a part is missing: what PLAYABLE_SQL catches.
    db.run(
      `INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
       VALUES ('01SET0000000000000000003', 'movie', 'mkv', 2000, 2, 'complete', 1, 3)`,
    );
    db.run(
      `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
       VALUES ('01SET0000000000000000003', 0, 0, 1000, -1001, 100, 'done')`,
    );

    expect(listPlayable(db)).toEqual([]);
  });

  test("lists newest first", () => {
    const db = fixture();
    completeSet(db, "01SETOLD00000000000000001", [{ off: 0, len: 10 }], 1_000);
    completeSet(db, "01SETNEW00000000000000001", [{ off: 0, len: 10 }], 2_000);

    expect(listPlayable(db).map((s) => s.setId)).toEqual([
      "01SETNEW00000000000000001",
      "01SETOLD00000000000000001",
    ]);
  });

  test("is empty for an empty index", () => {
    expect(listPlayable(fixture())).toEqual([]);
  });

  /**
   * The browser is given a catalog. It must not learn where the bytes live:
   * the channel id and message ids are exactly what the package format
   * encrypts, and a JSON response is not the place to hand them out.
   */
  test("tells the browser nothing about where the bytes live", () => {
    const db = fixture();
    completeSet(db, "01SET0000000000000000009", [{ off: 0, len: 1000 }]);

    const serialized = JSON.stringify(listPlayable(db));

    expect(serialized).not.toContain("1001");
    expect(serialized).not.toContain("chat");
    expect(serialized).not.toContain("message");
    expect(serialized).not.toContain("doc_id");
  });
});

describe("looking up one set", () => {
  test("finds a playable set by id", () => {
    const db = fixture();
    completeSet(db, "01SET0000000000000000005", [{ off: 0, len: 4096 }]);

    const found = playableSet(db, "01SET0000000000000000005");

    expect(found?.setId).toBe("01SET0000000000000000005");
    expect(found?.total).toBe(4096);
    expect(found?.partCount).toBe(1);
  });

  test("finds nothing for a set that is not playable, or not there", () => {
    const db = fixture();
    db.run(
      `INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
       VALUES ('01SET0000000000000000006', 'movie', 'mkv', 1000, 1, 'pending', 1, 3)`,
    );

    expect(playableSet(db, "01SET0000000000000000006")).toBeNull();
    expect(playableSet(db, "01NOSUCHSET00000000000001")).toBeNull();
  });
});

describe("part locations", () => {
  test("come back in order with their messages", () => {
    const db = fixture();
    completeSet(db, "01SET0000000000000000004", [
      { off: 0, len: 3_758_096_384 },
      { off: 3_758_096_384, len: 3_253_467_079 },
    ]);

    const located = partLocations(db, "01SET0000000000000000004");

    expect(located).toHaveLength(2);
    expect(located[0]!.span).toEqual({ idx: 0, off: 0, len: 3_758_096_384 });
    expect(located[1]!.span.off).toBe(3_758_096_384);
    expect(located[0]!.messageId).toBe(100);
    expect(located[1]!.messageId).toBe(101);
    expect(located[0]!.chatId).toBe(-1001);
  });

  test("skip a part with no message to fetch it from", () => {
    const db = fixture();
    db.run(
      `INSERT INTO parts(set_id, idx, byte_offset, byte_length, status)
       VALUES ('01SET0000000000000000007', 0, 0, 10, 'done')`,
    );

    expect(partLocations(db, "01SET0000000000000000007")).toEqual([]);
  });

  test("are empty for an unknown set", () => {
    expect(partLocations(fixture(), "01NOSUCHSET00000000000001")).toEqual([]);
  });
});
