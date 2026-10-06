use std::collections::HashSet;
use std::path::PathBuf;

use rusqlite::{Connection, OptionalExtension};
use tempfile::TempDir;

use crate::index::db;
use crate::index::merge::{Candidate, merge_from};

/// A local index, migrated to the current schema, backed by its own temp
/// dir. Mirrors `merge/tests.rs`'s own helper: each merge test file is
/// self-contained, so this one stays independent of that module's private
/// items.
fn open_local() -> (TempDir, Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    (dir, conn)
}

fn open_channel_at(schema_version: i64) -> (TempDir, PathBuf, Connection) {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("channel.db");
    let conn = crate::index::sqlite_init::open(&path).unwrap();
    conn.pragma_update(None, "journal_mode", "WAL").unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(schema_version) {
        conn.execute(stmt, []).unwrap();
    }
    db::set_meta(&conn, "schema_version", &schema_version.to_string()).unwrap();
    (dir, path, conn)
}

fn open_channel() -> (TempDir, PathBuf, Connection) {
    open_channel_at(mlib_spec::schema::SCHEMA_VERSION)
}

fn insert_set(conn: &Connection, set_id: &str, title: &str, status: &str, part_count: i64) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version, title)
         VALUES (?1, 'movie', 'mkv', 100, ?2, ?3, 1700000000, 4, ?4)",
        rusqlite::params![set_id, part_count, status, title],
    )
    .unwrap();
}

fn insert_part(conn: &Connection, set_id: &str, idx: i64, chat_id: i64, message_id: i64) {
    conn.execute(
        "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
         VALUES (?1, ?2, 0, 50, ?3, ?4, ?4, 'deadbeef', 'done')",
        rusqlite::params![set_id, idx, chat_id, message_id],
    )
    .unwrap();
}

fn insert_asset(conn: &Connection, set_id: &str, kind: &str, lang: &str, body: &str) {
    conn.execute(
        "INSERT INTO assets(set_id, kind, lang, body) VALUES (?1, ?2, ?3, ?4)",
        rusqlite::params![set_id, kind, lang, body],
    )
    .unwrap();
}

fn insert_subtitle_file(conn: &Connection, set_id: &str, message_id: i64, uploaded_at: i64) {
    conn.execute(
        "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at)
         VALUES (?1, 777, ?2, 1000,
                 'aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff001122', ?3)",
        rusqlite::params![set_id, message_id, uploaded_at],
    )
    .unwrap();
}

fn insert_subtitle_track(conn: &Connection, set_id: &str, track: i64, lang: &str) {
    conn.execute(
        "INSERT INTO subtitle_tracks(set_id, track, lang, forced, sdh, label)
         VALUES (?1, ?2, ?3, 0, 0, ?3)",
        rusqlite::params![set_id, track, lang],
    )
    .unwrap();
}

fn subtitle_file_row(conn: &Connection, set_id: &str) -> Option<(i64, i64)> {
    conn.query_row(
        "SELECT message_id, uploaded_at FROM subtitle_files WHERE set_id = ?1",
        [set_id],
        |r| Ok((r.get(0)?, r.get(1)?)),
    )
    .optional()
    .unwrap()
}

fn track_count(conn: &Connection, set_id: &str) -> i64 {
    conn.query_row(
        "SELECT COUNT(*) FROM subtitle_tracks WHERE set_id = ?1",
        [set_id],
        |r| r.get(0),
    )
    .unwrap()
}

fn subtitle_asset_count(conn: &Connection, set_id: &str) -> i64 {
    conn.query_row(
        "SELECT COUNT(*) FROM assets WHERE set_id = ?1 AND kind = 'subtitle'",
        [set_id],
        |r| r.get(0),
    )
    .unwrap()
}

fn keep_all(candidates: &[Candidate]) -> anyhow::Result<HashSet<String>> {
    Ok(candidates.iter().map(|c| c.set_id.clone()).collect())
}

fn keep_none(_candidates: &[Candidate]) -> anyhow::Result<HashSet<String>> {
    Ok(HashSet::new())
}

#[test]
fn a_channel_only_subtitle_bundle_for_a_shared_set_is_copied_with_its_tracks() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S1", "A Film", "complete", 1);
    insert_part(&local, "S1", 0, 100, 1001);
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S1", "A Film", "complete", 1);
    insert_part(&channel, "S1", 0, 100, 1001);
    insert_subtitle_file(&channel, "S1", 5001, 1_700_000_000);
    insert_subtitle_track(&channel, "S1", 0, "de");
    insert_subtitle_track(&channel, "S1", 1, "en");
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.subtitles_taken, 1);
    assert!(!report.channel_lacks_subtitles);
    assert_eq!(subtitle_file_row(&local, "S1"), Some((5001, 1_700_000_000)));
    assert_eq!(track_count(&local, "S1"), 2);

    let violations: i64 = local
        .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(violations, 0, "foreign keys must hold after a merge");
}

#[test]
fn a_channel_only_set_s_subtitle_bundle_is_copied_along_with_the_set() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S7", "New Film", "complete", 1);
    insert_part(&channel, "S7", 0, 100, 7001);
    insert_subtitle_file(&channel, "S7", 5001, 1_700_000_000);
    insert_subtitle_track(&channel, "S7", 0, "de");
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.sets_added, ["S7"]);
    assert_eq!(report.subtitles_taken, 1);
    assert_eq!(track_count(&local, "S7"), 1);
}

#[test]
fn a_newer_channel_bundle_replaces_the_local_one_and_its_tracks() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S1", "A Film", "complete", 1);
    insert_part(&local, "S1", 0, 100, 1001);
    insert_subtitle_file(&local, "S1", 4001, 1_700_000_000);
    insert_subtitle_track(&local, "S1", 0, "de");

    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S1", "A Film", "complete", 1);
    insert_part(&channel, "S1", 0, 100, 1001);
    insert_subtitle_file(&channel, "S1", 5001, 1_700_000_100);
    insert_subtitle_track(&channel, "S1", 0, "de");
    insert_subtitle_track(&channel, "S1", 1, "en");
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.subtitles_taken, 1);
    assert_eq!(subtitle_file_row(&local, "S1"), Some((5001, 1_700_000_100)));
    assert_eq!(track_count(&local, "S1"), 2);
}

#[test]
fn an_older_channel_bundle_does_not_replace_the_local_one() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S1", "A Film", "complete", 1);
    insert_part(&local, "S1", 0, 100, 1001);
    insert_subtitle_file(&local, "S1", 4001, 1_700_000_100);
    insert_subtitle_track(&local, "S1", 0, "de");

    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S1", "A Film", "complete", 1);
    insert_part(&channel, "S1", 0, 100, 1001);
    insert_subtitle_file(&channel, "S1", 5001, 1_700_000_000);
    insert_subtitle_track(&channel, "S1", 0, "de");
    insert_subtitle_track(&channel, "S1", 1, "en");
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.subtitles_taken, 0);
    assert_eq!(subtitle_file_row(&local, "S1"), Some((4001, 1_700_000_100)));
    assert_eq!(track_count(&local, "S1"), 1);
}

/// A set absent from the local index — here because it was not kept, the
/// same reason a real one ever would be, since `subtitle_files` itself is
/// foreign-keyed to `sets` and so can never name a set with no row at all.
#[test]
fn a_channel_only_candidate_that_is_not_kept_leaves_its_bundle_out_too() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S9", "Removed elsewhere", "complete", 1);
    insert_part(&channel, "S9", 0, 100, 9001);
    insert_subtitle_file(&channel, "S9", 5001, 1_700_000_000);
    insert_subtitle_track(&channel, "S9", 0, "de");
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_none).unwrap();

    assert_eq!(report.subtitles_taken, 0);
    assert_eq!(subtitle_file_row(&local, "S9"), None);
}

#[test]
fn a_v12_channel_index_has_no_subtitle_tables_and_flags_the_local_rows() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S1", "A Film", "complete", 1);
    insert_part(&local, "S1", 0, 100, 1001);
    insert_subtitle_file(&local, "S1", 4001, 1_700_000_000);
    insert_subtitle_track(&local, "S1", 0, "de");

    let (_channel_dir, channel_path, channel) = open_channel_at(12);
    insert_set(&channel, "S10", "Another Film", "complete", 1);
    insert_part(&channel, "S10", 0, 100, 10_001);
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.sets_added, ["S10"]);
    assert_eq!(report.subtitles_taken, 0);
    assert!(report.channel_lacks_subtitles);
}

#[test]
fn a_v12_channel_index_with_no_local_subtitle_rows_is_not_flagged() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel_at(12);
    insert_set(&channel, "S10", "A Film", "complete", 1);
    insert_part(&channel, "S10", 0, 100, 10_001);
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert!(!report.channel_lacks_subtitles);
}

#[test]
fn a_bundled_set_keeps_no_inline_subtitle_row_but_its_summary_survives() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S1", "A Film", "complete", 1);
    insert_part(&local, "S1", 0, 100, 1001);
    insert_asset(&local, "S1", "subtitle", "de", "old inline body");
    insert_asset(&local, "S1", "summary", "", "a summary");

    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S1", "A Film", "complete", 1);
    insert_part(&channel, "S1", 0, 100, 1001);
    insert_subtitle_file(&channel, "S1", 5001, 1_700_000_000);
    insert_subtitle_track(&channel, "S1", 0, "de");
    drop(channel);

    merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(subtitle_asset_count(&local, "S1"), 0);
    let summary: String = local
        .query_row(
            "SELECT body FROM assets WHERE set_id = 'S1' AND kind = 'summary'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(summary, "a summary");
}

/// Without the `fill_missing_assets` exclusion, a stray inline row the
/// channel still carries for an already-bundled set would be filled back in.
#[test]
fn inline_subtitle_rows_from_the_channel_are_not_filled_in_for_a_bundled_set() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S1", "A Film", "complete", 1);
    insert_part(&local, "S1", 0, 100, 1001);
    insert_subtitle_file(&local, "S1", 4001, 1_700_000_000);
    insert_subtitle_track(&local, "S1", 0, "de");

    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S1", "A Film", "complete", 1);
    insert_part(&channel, "S1", 0, 100, 1001);
    insert_asset(&channel, "S1", "subtitle", "fr", "legacy inline body");
    drop(channel);

    merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(subtitle_asset_count(&local, "S1"), 0);
}

#[test]
fn merging_the_same_subtitle_bundle_twice_reports_zero_the_second_time() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S1", "A Film", "complete", 1);
    insert_part(&local, "S1", 0, 100, 1001);
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S1", "A Film", "complete", 1);
    insert_part(&channel, "S1", 0, 100, 1001);
    insert_subtitle_file(&channel, "S1", 5001, 1_700_000_000);
    insert_subtitle_track(&channel, "S1", 0, "de");
    drop(channel);

    merge_from(&local, &channel_path, keep_all).unwrap();
    let second = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(second.subtitles_taken, 0);
    assert_eq!(track_count(&local, "S1"), 1);
}
