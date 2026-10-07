use rusqlite::Connection;

use super::*;

/// A version below 1 — an empty file, or a `user_version` nobody wrote —
/// has applied nothing, so the runner replays every step.
#[test]
fn an_empty_file_has_applied_nothing() {
    assert!(migrations_up_to(0).is_empty());
    assert!(migrations_up_to(-4).is_empty());
}

/// The runner counts the statements a file's version already applied and
/// skips that many, which is right only if each version's list starts with
/// the one before it, and every version adds something.
#[test]
fn each_version_extends_the_one_before_it() {
    for version in 0..VERSION {
        let before = migrations_up_to(version);
        let after = migrations_up_to(version + 1);
        assert!(after.len() > before.len(), "v{} adds nothing", version + 1);
        assert_eq!(
            after[..before.len()],
            before[..],
            "v{} rewrites history",
            version + 1
        );
    }
}

#[test]
fn a_version_past_the_newest_asks_for_nothing_more() {
    assert_eq!(migrations_up_to(VERSION + 3), migrations_up_to(VERSION));
    let every: usize = GROUPS.iter().map(|group| group.len()).sum();
    assert_eq!(migrations_up_to(VERSION).len(), every);
}

/// Every version's steps run on top of the version before it, and the
/// newest shape holds every table the store reads and writes.
#[test]
fn every_step_applies_in_turn_to_an_empty_file() {
    let conn = Connection::open_in_memory().unwrap();
    for version in 1..=VERSION {
        let applied = migrations_up_to(version - 1).len();
        for statement in migrations_up_to(version).into_iter().skip(applied) {
            conn.execute(statement, [])
                .unwrap_or_else(|err| panic!("v{version}: {err}\n{statement}"));
        }
    }
    let tables: Vec<String> = conn
        .prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
        .unwrap()
        .query_map([], |row| row.get(0))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert_eq!(
        tables,
        [
            "collection_items",
            "collections",
            "editors_choice",
            "kids",
            "preferences",
            "profiles",
            "progress",
            "state_meta",
            "stats_days",
            "stats_titles",
            "watched",
            "watchlist",
        ]
    );
}
