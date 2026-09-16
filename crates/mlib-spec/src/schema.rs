//! SQLite DDL for `library.db`. The uploader keeps this file locally as the
//! canonical index and pushes a snapshot to the channel as a pinned document.

pub const SCHEMA_VERSION: i64 = 3;

/// Statements grouped by the version they produce: `GROUPS[0]` takes a
/// database from nothing to version 1, `GROUPS[1]` from 1 to 2, and so on.
///
/// Grouping by version rather than replaying every statement on each open is
/// what lets a migration do something other than `CREATE ... IF NOT EXISTS`.
/// SQLite has no `ADD COLUMN IF NOT EXISTS`, so an idempotent-by-wording list
/// could never gain a column.
pub const GROUPS: &[&[&str]] = &[V1, V2, V3];

/// Every statement needed to reach `version` from an empty database. Used by
/// tests and by anyone reconstructing an older layout.
pub fn migrations_up_to(version: i64) -> Vec<&'static str> {
    GROUPS
        .iter()
        .take(version.max(0) as usize)
        .flat_map(|group| group.iter().copied())
        .collect()
}

/// v0 → v1: the original tables.
const V1: &[&str] = &[
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
const V2: &[&str] = &["ALTER TABLE sets ADD COLUMN chap TEXT"];

/// v2 → v3: where a set sat inside its collection.
///
/// A course nests unevenly — the one this was built for runs from one to four
/// folders deep — so a chapter number cannot describe the shape. The path can,
/// and the player rebuilds the tree by splitting it.
const V3: &[&str] = &["ALTER TABLE sets ADD COLUMN path TEXT"];

/// Playable invariant, as SQL usable in a WHERE clause on `sets s`.
pub const PLAYABLE_SQL: &str = "s.status = 'complete'
    AND s.part_count = (SELECT COUNT(*) FROM parts p WHERE p.set_id = s.set_id AND p.status = 'done')
    AND s.total = (SELECT COALESCE(SUM(byte_length), 0) FROM parts p WHERE p.set_id = s.set_id)";

#[cfg(test)]
mod tests {
    #[test]
    fn the_first_group_creates_the_tables_idempotently() {
        let v1 = super::GROUPS[0];
        assert!(v1.len() >= 4);
        assert!(v1.iter().all(|m| m.contains("IF NOT EXISTS")));
    }

    /// Later groups run once, gated by the recorded version, so they are free
    /// to use statements SQLite cannot express idempotently.
    #[test]
    fn there_is_one_group_per_version() {
        assert_eq!(super::GROUPS.len() as i64, super::SCHEMA_VERSION);
    }

    #[test]
    fn migrations_up_to_accumulates_groups() {
        assert!(super::migrations_up_to(0).is_empty());
        assert_eq!(super::migrations_up_to(1).len(), super::GROUPS[0].len());
        assert_eq!(
            super::migrations_up_to(2).len(),
            super::GROUPS[0].len() + super::GROUPS[1].len()
        );
        assert_eq!(
            super::migrations_up_to(3).len(),
            super::GROUPS.iter().map(|g| g.len()).sum::<usize>()
        );
    }
}
