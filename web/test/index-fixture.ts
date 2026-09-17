/**
 * The uploader's tables, as the player expects to find them.
 *
 * Built here rather than imported from the uploader: the player only ever
 * reads, so the columns it names are its whole dependency on the layout, and
 * what must not drift is the *definition* of playable, which is pinned from
 * the Rust side by `crates/mediagram/tests/shared_playable_sql.rs`.
 *
 * One copy, though. Four suites were each writing their own CREATE TABLE, and
 * the trimmed copies had already lost columns the full schema declares —
 * which is how a fixture ends up accepting an INSERT the real index would
 * reject.
 */

import { Database } from "bun:sqlite";

const SETS = `CREATE TABLE sets(
    set_id TEXT PRIMARY KEY, kind TEXT NOT NULL,
    tmdb INTEGER, tvdb INTEGER, imdb TEXT,
    show TEXT, path TEXT, title TEXT, year INTEGER,
    season INTEGER, episode TEXT, abs INTEGER,
    quality TEXT, hdr TEXT, container TEXT NOT NULL,
    vcodec TEXT, acodec TEXT,
    alang TEXT NOT NULL DEFAULT '[]', slang TEXT NOT NULL DEFAULT '[]',
    duration INTEGER, variant TEXT, group_key TEXT,
    total INTEGER NOT NULL, part_count INTEGER NOT NULL,
    set_hash TEXT, status TEXT NOT NULL DEFAULT 'pending',
    created_at INTEGER NOT NULL, spec_version INTEGER NOT NULL, chap TEXT)`;

const PARTS = `CREATE TABLE parts(
    set_id TEXT NOT NULL, idx INTEGER NOT NULL,
    byte_offset INTEGER NOT NULL, byte_length INTEGER NOT NULL,
    chat_id INTEGER, message_id INTEGER, doc_id INTEGER, sha256 TEXT,
    status TEXT NOT NULL DEFAULT 'pending', verified_at INTEGER,
    PRIMARY KEY(set_id, idx))`;

const ASSETS = `CREATE TABLE assets(
    set_id TEXT NOT NULL, kind TEXT NOT NULL,
    lang TEXT NOT NULL DEFAULT '', body TEXT NOT NULL,
    PRIMARY KEY(set_id, kind, lang))`;

/** An in-memory index with no rows in it. */
export function emptyIndex(): Database {
  const db = new Database(":memory:");
  for (const ddl of [SETS, PARTS, ASSETS]) db.run(ddl);
  return db;
}
