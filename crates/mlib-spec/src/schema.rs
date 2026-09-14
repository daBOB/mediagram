//! SQLite DDL for `library.db`. The uploader keeps this file locally as the
//! canonical index and pushes a snapshot to the channel as a pinned document.

pub const SCHEMA_VERSION: i64 = 1;

/// Statements applied in order on an empty database. Each is idempotent.
pub const MIGRATIONS: &[&str] = &[
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

/// Playable invariant, as SQL usable in a WHERE clause on `sets s`.
pub const PLAYABLE_SQL: &str = "s.status = 'complete'
    AND s.part_count = (SELECT COUNT(*) FROM parts p WHERE p.set_id = s.set_id AND p.status = 'done')
    AND s.total = (SELECT COALESCE(SUM(byte_length), 0) FROM parts p WHERE p.set_id = s.set_id)";

#[cfg(test)]
mod tests {
    #[test]
    fn migrations_are_present_and_idempotent_in_wording() {
        assert!(super::MIGRATIONS.len() >= 4);
        assert!(
            super::MIGRATIONS
                .iter()
                .all(|m| m.contains("IF NOT EXISTS"))
        );
    }
}
