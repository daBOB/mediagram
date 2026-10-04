use super::*;
use crate::state::StateDb;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

#[test]
fn creating_and_listing_round_trips_a_name() {
    let (_dir, db) = db();
    db.with(|conn| create(conn, "André", false)).unwrap();
    let names: Vec<String> = db.with(list).unwrap().into_iter().map(|p| p.name).collect();
    assert_eq!(names, vec!["André".to_string()]);
}

#[test]
fn a_blank_name_creates_nothing() {
    let (_dir, db) = db();
    assert_eq!(db.with(|conn| create(conn, "   ", false)).unwrap(), None);
}

#[test]
fn choosing_an_unknown_id_is_reported_false_and_remembers_nothing() {
    let (_dir, db) = db();
    assert!(!db.with(|conn| choose(conn, "nope")).unwrap());
    assert_eq!(db.with(chosen).unwrap(), None);
}

#[test]
fn choosing_a_real_profile_is_remembered() {
    let (_dir, db) = db();
    let id = db
        .with(|conn| create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id;
    db.with(|conn| choose(conn, &id)).unwrap();
    assert_eq!(db.with(chosen).unwrap(), Some(id));
}

/// A second machine's document mentions a viewer this one has never
/// seen: `profile_named` has to create them rather than drop the sync.
#[test]
fn profile_named_creates_an_unseen_viewer_and_reuses_them_after() {
    let (_dir, db) = db();
    let first = db
        .with(|conn| profile_named(conn, "andré", Some("André")))
        .unwrap()
        .unwrap();
    let second = db
        .with(|conn| profile_named(conn, "ANDRÉ", Some("ANDRÉ")))
        .unwrap()
        .unwrap();
    assert_eq!(
        first, second,
        "the same viewer, spelled differently, is one profile"
    );
}

#[test]
fn deleting_an_unknown_id_reports_false() {
    let (_dir, db) = db();
    assert!(!db.with(|conn| delete(conn, "nope")).unwrap());
}

#[test]
fn deleting_a_real_profile_removes_it_from_the_list() {
    let (_dir, db) = db();
    let id = db.with(|conn| create(conn, "André", false)).unwrap().unwrap().id;

    assert!(db.with(|conn| delete(conn, &id)).unwrap());

    assert_eq!(db.with(list).unwrap(), Vec::new());
}

/// `chosen` checks the profile still exists on every read rather than
/// trusting what was last written, so deleting the chosen profile clears it
/// without `delete` having to know it was the one chosen.
#[test]
fn deleting_the_chosen_profile_clears_it() {
    let (_dir, db) = db();
    let id = db.with(|conn| create(conn, "André", false)).unwrap().unwrap().id;
    db.with(|conn| choose(conn, &id)).unwrap();

    db.with(|conn| delete(conn, &id)).unwrap();

    assert_eq!(db.with(chosen).unwrap(), None);
}

/// A kid made by sync, or from before limits, starts at FSK 12 dated 0 —
/// older than any limit a parent chooses, so the first real choice, made
/// here or synced in, wins. A grown-up has no limit at all.
#[test]
fn a_kid_made_without_a_chosen_limit_stores_twelve_dated_zero() {
    let (_dir, db) = db();
    let mia = db.with(|c| create(c, "Mia", true)).unwrap().unwrap().id;
    let bea = db.with(|c| create(c, "Bea", false)).unwrap().unwrap().id;
    let limit = |id: &str| -> (Option<i64>, i64) {
        db.with(|c| {
            c.query_row(
                "SELECT kids_age, kids_age_updated_at FROM profiles WHERE id = ?1",
                [id],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
        })
        .unwrap()
    };
    assert_eq!(limit(&mia), (Some(12), 0));
    assert_eq!(limit(&bea), (None, 0));
}
