use std::collections::HashSet;
use std::path::PathBuf;

use rusqlite::Connection;
use tempfile::TempDir;

use super::*;
use crate::index::db;

/// A local index, migrated to the current schema, backed by its own temp dir.
fn open_local() -> (TempDir, Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    (dir, conn)
}

/// A standalone channel snapshot at `schema_version`, as its own file. Kept
/// apart from `local`'s connection the way a downloaded snapshot really is:
/// nothing here is `ATTACH`ed until `merge_from` does it.
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

/// `keep_if_live` that keeps every candidate — the "messages still exist"
/// answer for tests that are not exercising the removal check itself.
fn keep_all(candidates: &[Candidate]) -> Result<HashSet<String>> {
    Ok(candidates.iter().map(|c| c.set_id.clone()).collect())
}

fn keep_none(_candidates: &[Candidate]) -> Result<HashSet<String>> {
    Ok(HashSet::new())
}

#[test]
fn a_channel_only_complete_set_is_added_with_its_parts_and_assets() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S1", "A Film", "complete", 2);
    insert_part(&channel, "S1", 0, 100, 1001);
    insert_part(&channel, "S1", 1, 100, 1002);
    insert_asset(&channel, "S1", "subtitle", "en", "hello");
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.sets_added, ["S1"]);
    assert!(report.sets_skipped_pending.is_empty());
    assert!(report.sets_skipped_removed.is_empty());
    assert!(report.conflicts.is_empty());

    let title: String = local
        .query_row("SELECT title FROM sets WHERE set_id = 'S1'", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(title, "A Film");
    let parts: i64 = local
        .query_row("SELECT COUNT(*) FROM parts WHERE set_id = 'S1'", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(parts, 2);
    let assets: i64 = local
        .query_row("SELECT COUNT(*) FROM assets WHERE set_id = 'S1'", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(assets, 1);

    let violations: i64 = local
        .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(violations, 0, "foreign keys must hold after a merge");
}

#[test]
fn a_pending_channel_set_is_skipped_without_asking_the_closure() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S2", "Unfinished", "pending", 3);
    insert_part(&channel, "S2", 0, 100, 2001);
    drop(channel);

    let report = merge_from(&local, &channel_path, |candidates| {
        assert!(
            candidates.is_empty(),
            "a pending set must never reach the closure"
        );
        Ok(HashSet::new())
    })
    .unwrap();

    assert!(report.sets_added.is_empty());
    assert_eq!(report.sets_skipped_pending, ["S2"]);
    let count: i64 = local
        .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get(0))
        .unwrap();
    assert_eq!(count, 0);
}

#[test]
fn a_complete_set_whose_messages_are_gone_is_skipped() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S3", "Removed Elsewhere", "complete", 1);
    insert_part(&channel, "S3", 0, 100, 3001);
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_none).unwrap();

    assert!(report.sets_added.is_empty());
    assert_eq!(report.sets_skipped_removed, ["S3"]);
    let count: i64 = local
        .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get(0))
        .unwrap();
    assert_eq!(count, 0);
}

#[test]
fn a_shared_set_with_differing_metadata_is_reported_and_not_overwritten() {
    let (_local_dir, local) = open_local();
    insert_set(&local, "S4", "Local Title", "complete", 1);
    insert_part(&local, "S4", 0, 100, 4001);
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S4", "Channel Title", "complete", 1);
    insert_part(&channel, "S4", 0, 100, 4001);
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.conflicts, ["S4"]);
    assert!(report.sets_added.is_empty());
    let title: String = local
        .query_row("SELECT title FROM sets WHERE set_id = 'S4'", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(
        title, "Local Title",
        "merge_from must not pick a side itself"
    );
}

#[test]
fn a_v7_channel_index_has_no_popularity_column_and_still_merges() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel_at(7);
    channel
        .execute(
            "INSERT INTO shows(source, kind, id, overview, certification)
             VALUES ('tmdb', 'movie', 1, 'a film', '12')",
            [],
        )
        .unwrap();
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.shows_added, 1);
    let (overview, certification, popularity): (String, String, Option<f64>) = local
        .query_row(
            "SELECT overview, certification, popularity FROM shows WHERE source='tmdb' AND kind='movie' AND id=1",
            [],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
        .unwrap();
    assert_eq!(overview, "a film");
    assert_eq!(certification, "12");
    assert_eq!(
        popularity, None,
        "the channel had no popularity column to copy"
    );
}

#[test]
fn shows_null_fill_only_touches_columns_the_local_row_lacks() {
    let (_local_dir, local) = open_local();
    local
        .execute(
            "INSERT INTO shows(source, kind, id, overview) VALUES ('tmdb', 'movie', 2, 'local overview')",
            [],
        )
        .unwrap();
    let (_channel_dir, channel_path, channel) = open_channel();
    channel
        .execute(
            "INSERT INTO shows(source, kind, id, overview, rating) VALUES ('tmdb', 'movie', 2, 'channel overview', 8.5)",
            [],
        )
        .unwrap();
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.shows_added, 0);
    assert_eq!(report.shows_filled, 1);
    let (overview, rating): (String, f64) = local
        .query_row(
            "SELECT overview, rating FROM shows WHERE source='tmdb' AND kind='movie' AND id=2",
            [],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .unwrap();
    assert_eq!(
        overview, "local overview",
        "an existing value is never replaced"
    );
    assert_eq!(rating, 8.5, "a NULL column is filled from the channel");
}

#[test]
fn meta_is_never_touched_by_a_merge() {
    let (_local_dir, local) = open_local();
    db::set_meta(&local, "last_push_at", "123").unwrap();
    let (_channel_dir, channel_path, channel) = open_channel();
    db::set_meta(&channel, "last_push_at", "999").unwrap();
    insert_set(&channel, "S5", "Something", "complete", 1);
    insert_part(&channel, "S5", 0, 100, 5001);
    drop(channel);

    merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(
        db::get_meta(&local, "last_push_at").unwrap().as_deref(),
        Some("123")
    );
}

#[test]
fn merging_the_same_channel_snapshot_twice_adds_nothing_the_second_time() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    insert_set(&channel, "S6", "Once", "complete", 1);
    insert_part(&channel, "S6", 0, 100, 6001);
    insert_asset(&channel, "S6", "subtitle", "en", "hi");
    drop(channel);

    let first = merge_from(&local, &channel_path, keep_all).unwrap();
    assert_eq!(first.sets_added, ["S6"]);

    let second = merge_from(&local, &channel_path, keep_all).unwrap();
    assert!(second.sets_added.is_empty());
    assert!(second.conflicts.is_empty());
    assert_eq!(second.shows_filled, 0);

    let parts: i64 = local
        .query_row("SELECT COUNT(*) FROM parts WHERE set_id = 'S6'", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(parts, 1, "a second merge must not duplicate rows");
}

#[test]
fn show_text_is_only_filled_from_a_row_in_the_same_language() {
    let (_local_dir, local) = open_local();
    local
        .execute(
            "INSERT INTO shows(source, kind, id, lang) VALUES ('tmdb', 'movie', 3, 'de-DE')",
            [],
        )
        .unwrap();
    let (_channel_dir, channel_path, channel) = open_channel();
    channel
        .execute(
            "INSERT INTO shows(source, kind, id, lang, overview, rating)
             VALUES ('tmdb', 'movie', 3, 'en-US', 'an English overview', 7.1)",
            [],
        )
        .unwrap();
    drop(channel);

    merge_from(&local, &channel_path, keep_all).unwrap();

    let (overview, rating): (Option<String>, Option<f64>) = local
        .query_row(
            "SELECT overview, rating FROM shows WHERE id = 3",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .unwrap();
    assert_eq!(overview, None, "English text must not fill a German row");
    assert_eq!(rating, Some(7.1), "a figure reads the same in any language");
}

#[test]
fn credits_and_franchises_this_index_lacks_are_copied_whole() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    channel
        .execute(
            "INSERT INTO credits(source, kind, id, ord, person_id, name, role, dept, profile)
             VALUES ('tmdb', 'movie', 550, 0, 1, 'Lead', 'Hero', 'cast', '/a.jpg')",
            [],
        )
        .unwrap();
    channel
        .execute(
            "INSERT INTO franchises(source, id, name, overview)
             VALUES ('tmdb', 115, 'A Franchise', 'An overview.')",
            [],
        )
        .unwrap();
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.credits_added, 1);
    assert_eq!(report.franchises_added, 1);
    let (name, profile): (String, String) = local
        .query_row(
            "SELECT name, profile FROM credits WHERE source='tmdb' AND kind='movie' AND id=550",
            [],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .unwrap();
    assert_eq!(name, "Lead");
    assert_eq!(profile, "/a.jpg");
    let franchise_name: String = local
        .query_row(
            "SELECT name FROM franchises WHERE source='tmdb' AND id=115",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(franchise_name, "A Franchise");
}

#[test]
fn a_title_already_credited_locally_is_left_alone() {
    let (_local_dir, local) = open_local();
    local
        .execute(
            "INSERT INTO credits(source, kind, id, ord, person_id, name, role, dept)
             VALUES ('tmdb', 'movie', 550, 0, 9, 'Local Cast', NULL, 'cast')",
            [],
        )
        .unwrap();
    let (_channel_dir, channel_path, channel) = open_channel();
    channel
        .execute(
            "INSERT INTO credits(source, kind, id, ord, person_id, name, role, dept)
             VALUES ('tmdb', 'movie', 550, 0, 1, 'Channel Cast', NULL, 'cast')",
            [],
        )
        .unwrap();
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(
        report.credits_added, 0,
        "a title already credited is never topped up"
    );
    let count: i64 = local
        .query_row("SELECT COUNT(*) FROM credits", [], |r| r.get(0))
        .unwrap();
    assert_eq!(count, 1);
}

#[test]
fn a_v8_channel_index_has_no_credits_table_and_still_merges() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel_at(8);
    insert_set(&channel, "S7", "A Film", "complete", 1);
    insert_part(&channel, "S7", 0, 100, 7001);
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.sets_added, ["S7"]);
    assert_eq!(report.credits_added, 0);
    assert_eq!(report.franchises_added, 0);
}

#[test]
fn artwork_this_index_lacks_is_copied_whole() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel();
    channel
        .execute(
            "INSERT INTO artwork(key, mime, bytes) VALUES ('title-terra-x', 'image/jpeg', X'01')",
            [],
        )
        .unwrap();
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.artwork_added, 1);
    let (mime, bytes): (String, Vec<u8>) = local
        .query_row(
            "SELECT mime, bytes FROM artwork WHERE key = 'title-terra-x'",
            [],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .unwrap();
    assert_eq!(mime, "image/jpeg");
    assert_eq!(bytes, vec![1]);
}

#[test]
fn a_key_already_held_locally_is_never_overwritten_by_the_channel() {
    let (_local_dir, local) = open_local();
    local
        .execute(
            "INSERT INTO artwork(key, mime, bytes) VALUES ('title-terra-x', 'image/jpeg', X'01')",
            [],
        )
        .unwrap();
    let (_channel_dir, channel_path, channel) = open_channel();
    channel
        .execute(
            "INSERT INTO artwork(key, mime, bytes) VALUES ('title-terra-x', 'image/png', X'02')",
            [],
        )
        .unwrap();
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.artwork_added, 0);
    let mime: String = local
        .query_row(
            "SELECT mime FROM artwork WHERE key = 'title-terra-x'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(mime, "image/jpeg", "the local image is never replaced");
}

#[test]
fn a_v9_channel_index_has_no_artwork_table_and_still_merges() {
    let (_local_dir, local) = open_local();
    let (_channel_dir, channel_path, channel) = open_channel_at(9);
    insert_set(&channel, "S8", "A Film", "complete", 1);
    insert_part(&channel, "S8", 0, 100, 8001);
    drop(channel);

    let report = merge_from(&local, &channel_path, keep_all).unwrap();

    assert_eq!(report.sets_added, ["S8"]);
    assert_eq!(report.artwork_added, 0);
}
