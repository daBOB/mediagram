//! `v1` through `v6` of `schema.rs`'s migration groups, split out to keep
//! that file under this crate's line limit. History nobody needs to touch
//! again; a new group belongs in `schema.rs` itself, beside `V7` onward.

/// v0 → v1: the original tables.
pub(super) const V1: &[&str] = &[
    "CREATE TABLE IF NOT EXISTS sets(
        set_id TEXT PRIMARY KEY,
        kind TEXT NOT NULL,
        tmdb INTEGER, tvdb INTEGER, imdb TEXT,
        show TEXT, title TEXT, year INTEGER,
        season INTEGER, episode TEXT, abs INTEGER,
        quality TEXT, hdr TEXT, container TEXT NOT NULL,
        vcodec TEXT, acodec TEXT,
        alang TEXT NOT NULL DEFAULT '[]', slang TEXT NOT NULL DEFAULT '[]',
        duration INTEGER, variant TEXT, group_key TEXT,
        total INTEGER NOT NULL, part_count INTEGER NOT NULL,
        set_hash TEXT,
        status TEXT NOT NULL DEFAULT 'pending',
        created_at INTEGER NOT NULL,
        spec_version INTEGER NOT NULL
    )",
    "CREATE TABLE IF NOT EXISTS parts(
        set_id TEXT NOT NULL REFERENCES sets(set_id) ON DELETE CASCADE,
        idx INTEGER NOT NULL,
        byte_offset INTEGER NOT NULL,
        byte_length INTEGER NOT NULL,
        chat_id INTEGER, message_id INTEGER,
        doc_id INTEGER,
        sha256 TEXT,
        status TEXT NOT NULL DEFAULT 'pending',
        verified_at INTEGER,
        PRIMARY KEY(set_id, idx)
    )",
    "CREATE INDEX IF NOT EXISTS parts_message ON parts(chat_id, message_id)",
    "CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
];

/// v1 → v2: chapter titles for courses. `group_key`, declared in v1 and never
/// populated until now, carries the collection id.
pub(super) const V2: &[&str] = &["ALTER TABLE sets ADD COLUMN chap TEXT"];

/// v2 → v3: where a set sat inside its collection.
///
/// A course nests unevenly — the one this was built for runs from one to four
/// folders deep — so a chapter number cannot describe the shape. The path can,
/// and the player rebuilds the tree by splitting it.
pub(super) const V3: &[&str] = &["ALTER TABLE sets ADD COLUMN path TEXT"];

/// v3 → v4: text that belongs to a set.
///
/// Subtitles and an optional summary live here rather than as their own
/// channel messages. A whole course's subtitles are about 2 MB, which rides
/// the published package unnoticed, and a player can then show a summary or
/// attach a subtitle track without a Telegram round trip.
///
/// `lang` is `''` for anything not language-specific, never NULL: SQLite
/// treats NULLs in a primary key as distinct, which would let duplicates in.
pub(super) const V4: &[&str] = &["CREATE TABLE IF NOT EXISTS assets(
        set_id TEXT NOT NULL REFERENCES sets(set_id) ON DELETE CASCADE,
        kind TEXT NOT NULL,
        lang TEXT NOT NULL DEFAULT '',
        body TEXT NOT NULL,
        PRIMARY KEY(set_id, kind, lang)
    )"];

/// v4 → v5: what a provider says about a show, as opposed to about a file.
///
/// A show is not otherwise an entity here — it is what you get by grouping
/// sets on their provider id — so there was nowhere to put a synopsis that
/// belongs to the whole of it. Keyed the way a poster key is, because TMDB
/// numbers films and series independently and 550 means two different things.
///
/// `lang` records which language the text is in rather than keying by it: a
/// library fetches one language at a time, so asking again in another should
/// replace the text, not accumulate beside it.
pub(super) const V5: &[&str] = &["CREATE TABLE IF NOT EXISTS shows(
        source TEXT NOT NULL,
        kind TEXT NOT NULL,
        id INTEGER NOT NULL,
        lang TEXT NOT NULL DEFAULT '',
        overview TEXT, tagline TEXT, genres TEXT, rating REAL,
        network TEXT, status TEXT, first_air TEXT, last_air TEXT,
        PRIMARY KEY(source, kind, id)
    )"];

/// v5 → v6: how much of a show exists, as against how much is held.
///
/// The index can count what it has; only the provider knows what there is.
/// Without these a library cannot tell a complete show from the first season
/// of one, which is the question anyone browsing a shelf actually has.
pub(super) const V6: &[&str] = &[
    "ALTER TABLE shows ADD COLUMN total_seasons INTEGER",
    "ALTER TABLE shows ADD COLUMN total_episodes INTEGER",
];
