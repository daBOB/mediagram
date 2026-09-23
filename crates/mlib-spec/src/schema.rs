//! SQLite DDL for `library.db`. The uploader keeps this file locally as the
//! canonical index and pushes a snapshot to the channel as a pinned document.

pub const SCHEMA_VERSION: i64 = 7;

/// The oldest index a *reader* of someone else's snapshot still accepts.
///
/// A channel snapshot is written by whichever machine uploads, and that
/// machine is upgraded on its own schedule; refusing its snapshot until then
/// would stop every reader. v7 only added `shows.certification`, which every
/// reader treats as optional. The web player's `OLDEST_READABLE_SCHEMA`
/// (`web/src/catalog.ts`) is the same number. The uploader's own index is
/// still held to [`SCHEMA_VERSION`]: that one it can migrate.
pub const OLDEST_READABLE_SCHEMA: i64 = 6;

/// The index's file name, wherever a copy of it sits: the uploader's data
/// directory, the snapshot pinned in the channel, and a metadata package all
/// carry it under this one name, which is how a reader finds it in each.
pub const INDEX_FILE: &str = "library.db";

/// Statements grouped by the version they produce: `GROUPS[0]` takes a
/// database from nothing to version 1, `GROUPS[1]` from 1 to 2, and so on.
///
/// Grouping by version rather than replaying every statement on each open is
/// what lets a migration do something other than `CREATE ... IF NOT EXISTS`.
/// SQLite has no `ADD COLUMN IF NOT EXISTS`, so an idempotent-by-wording list
/// could never gain a column.
pub const GROUPS: &[&[&str]] = &[V1, V2, V3, V4, V5, V6, V7];

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

/// v3 → v4: text that belongs to a set.
///
/// Subtitles and an optional summary live here rather than as their own
/// channel messages. A whole course's subtitles are about 2 MB, which rides
/// the published package unnoticed, and a player can then show a summary or
/// attach a subtitle track without a Telegram round trip.
///
/// `lang` is `''` for anything not language-specific, never NULL: SQLite
/// treats NULLs in a primary key as distinct, which would let duplicates in.
const V4: &[&str] = &["CREATE TABLE IF NOT EXISTS assets(
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
const V5: &[&str] = &["CREATE TABLE IF NOT EXISTS shows(
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
const V6: &[&str] = &[
    "ALTER TABLE shows ADD COLUMN total_seasons INTEGER",
    "ALTER TABLE shows ADD COLUMN total_episodes INTEGER",
];

/// v6 → v7: the age rating a title carries in the library's country.
///
/// What decides whether a title may sit on the Kids shelf without anyone
/// having marked it. Text, as the provider writes it (`12`, `FSK 16` is never
/// the form), because some countries rate with letters.
const V7: &[&str] = &["ALTER TABLE shows ADD COLUMN certification TEXT"];

/// How `sets.status` and `parts.status` spell each state. Written once here,
/// beside the one SQL fragment that has to spell them inline; every other
/// query binds them as parameters. The player reads the same spellings.
pub const SET_PENDING: &str = "pending";
pub const SET_COMPLETE: &str = "complete";
pub const PART_PENDING: &str = "pending";
pub const PART_DONE: &str = "done";

/// Playable invariant, as SQL usable in a WHERE clause on `sets s`.
pub const PLAYABLE_SQL: &str = "s.status = 'complete'
    AND s.part_count = (SELECT COUNT(*) FROM parts p WHERE p.set_id = s.set_id AND p.status = 'done')
    AND s.total = (SELECT COALESCE(SUM(byte_length), 0) FROM parts p WHERE p.set_id = s.set_id)";

#[cfg(test)]
mod tests {
    /// `PLAYABLE_SQL` is a `const`, so it cannot be built from the spellings
    /// above; this holds the two together instead.
    #[test]
    fn the_playable_gate_spells_states_as_the_constants_do() {
        let gate = super::PLAYABLE_SQL;
        assert!(gate.contains(&format!("s.status = '{}'", super::SET_COMPLETE)));
        assert!(gate.contains(&format!("p.status = '{}'", super::PART_DONE)));
    }

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
        // Every group, whatever the current version, so this keeps holding
        // as versions are added.
        assert_eq!(
            super::migrations_up_to(super::SCHEMA_VERSION).len(),
            super::GROUPS.iter().map(|g| g.len()).sum::<usize>()
        );
    }
}
