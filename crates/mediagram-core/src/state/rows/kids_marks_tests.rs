use super::*;
use crate::state::StateDb;
use crate::state::record::MAX_STAMP;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

/// `(marked_at, removed_at, age)` as stored.
fn stored(db: &StateDb, set_id: &str) -> (i64, Option<i64>, Option<i64>) {
    db.with(|c| {
        c.query_row(
            "SELECT marked_at, removed_at, age FROM kids WHERE set_id = ?1",
            [set_id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
    })
    .unwrap()
}

/// A mark another device stamped with a clock running far ahead of this one.
fn imported(db: &StateDb, set_id: &str, at: i64, age: Option<i64>) {
    db.with(|c| {
        c.execute(
            "INSERT INTO kids(set_id, marked_at, age) VALUES (?1, ?2, ?3)",
            params![set_id, at, age],
        )
    })
    .unwrap();
}

const AHEAD: i64 = 9_999_999_999_999;

#[test]
fn a_mark_from_six_is_in_both_lists_and_one_from_twelve_only_in_kids() {
    let (_dir, db) = db();
    db.with(|c| set_kids(c, "six", Some(6))).unwrap();
    db.with(|c| set_kids(c, "twelve", Some(12))).unwrap();
    let mut all = db.with(kids).unwrap();
    all.sort();
    assert_eq!(all, ["six", "twelve"]);
    assert_eq!(db.with(kids_from_six).unwrap(), ["six"]);
}

#[test]
fn an_age_other_than_six_is_stored_as_from_twelve() {
    let (_dir, db) = db();
    db.with(|c| set_kids(c, "x", Some(7))).unwrap();
    assert_eq!(stored(&db, "x").2, None);
    assert_eq!(db.with(kids).unwrap(), ["x"]);
}

/// Both directions move the clock past whatever the row carries, or a
/// merge would settle an equal-time pair as an older build's echo.
#[test]
fn a_new_age_on_a_live_mark_is_stamped_past_a_faster_clock_either_way() {
    let (_dir, db) = db();
    imported(&db, "up", AHEAD, None);
    db.with(|c| set_kids(c, "up", Some(6))).unwrap();
    assert_eq!(stored(&db, "up"), (AHEAD + 1, None, Some(6)));

    imported(&db, "down", AHEAD, Some(6));
    db.with(|c| set_kids(c, "down", Some(12))).unwrap();
    assert_eq!(stored(&db, "down"), (AHEAD + 1, None, None));
}

#[test]
fn marking_again_at_the_same_age_changes_nothing() {
    let (_dir, db) = db();
    imported(&db, "x", AHEAD, Some(6));
    db.with(|c| set_kids(c, "x", Some(6))).unwrap();
    assert_eq!(stored(&db, "x"), (AHEAD, None, Some(6)));
}

#[test]
fn a_removal_is_a_tombstone_stamped_past_the_mark_it_takes_off() {
    let (_dir, db) = db();
    imported(&db, "x", AHEAD, Some(6));
    db.with(|c| set_kids(c, "x", None)).unwrap();
    assert!(db.with(kids).unwrap().is_empty());
    assert!(db.with(kids_from_six).unwrap().is_empty());
    assert_eq!(stored(&db, "x").1, Some(AHEAD + 1));
}

/// A re-mark after a removal is clamped past the removal, the same way.
#[test]
fn marking_again_after_a_removal_is_stamped_past_it() {
    let (_dir, db) = db();
    db.with(|c| {
        c.execute(
            "INSERT INTO kids(set_id, marked_at, removed_at) VALUES ('x', 1, ?1)",
            [AHEAD],
        )
    })
    .unwrap();
    db.with(|c| set_kids(c, "x", Some(12))).unwrap();
    assert_eq!(stored(&db, "x"), (AHEAD + 1, None, None));
}

/// No stamp steps past the bound every peer reads: a change at the top
/// still lands, at the bound itself, rather than as a stamp no peer keeps.
#[test]
fn a_change_at_the_top_of_the_range_stays_within_it() {
    let (_dir, db) = db();
    let top = MAX_STAMP as i64;
    imported(&db, "x", top, None);
    db.with(|c| set_kids(c, "x", Some(6))).unwrap();
    assert_eq!(stored(&db, "x"), (top, None, Some(6)));
    db.with(|c| set_kids(c, "x", None)).unwrap();
    assert_eq!(stored(&db, "x").1, Some(top));
}
