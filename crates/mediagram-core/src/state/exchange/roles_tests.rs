use rusqlite::types::FromSql;

use crate::state::exchange::{export_record, import_merged};
use crate::state::merge::{MergedProfile, MergedState, merge_states};
use crate::state::record::{
    AdminClaim, KidsAge, PinRecord, ProfileRoles, SyncRecord, parse_record,
};
use crate::state::{StateDb, profiles};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn make(db: &StateDb, name: &str, kids: bool) -> String {
    db.with(|c| profiles::create(c, name, kids))
        .unwrap()
        .unwrap()
        .id
}

fn sql(db: &StateDb, statement: &str) {
    db.with(|c| c.execute_batch(statement)).unwrap();
}

fn value<T: FromSql>(db: &StateDb, query: &str) -> T {
    db.with(|c| c.query_row(query, [], |r| r.get(0))).unwrap()
}

fn viewer(name: &str, kids: bool, roles: ProfileRoles) -> MergedProfile {
    MergedProfile {
        name: name.to_lowercase(),
        display_name: name.into(),
        kids,
        roles,
        ..Default::default()
    }
}

fn import(db: &StateDb, viewers: Vec<MergedProfile>) -> u64 {
    let merged = MergedState {
        profiles: viewers,
        ..Default::default()
    };
    db.with(|c| import_merged(c, &merged)).unwrap()
}

/// A first PIN: set where its grown-up had none.
fn pin(hash: char, at: f64) -> PinRecord {
    PinRecord {
        hash: hash.to_string().repeat(64),
        salt: "b".repeat(32),
        updated_at: at,
        proven: false,
    }
}

/// A PIN set by someone who knew the one before, or the admin's reset.
fn proven(hash: char, at: f64) -> PinRecord {
    PinRecord {
        proven: true,
        ..pin(hash, at)
    }
}

fn roles_of(record: &SyncRecord, name: &str) -> ProfileRoles {
    record
        .profiles
        .iter()
        .find(|p| p.name == name)
        .unwrap()
        .roles
        .clone()
}

#[test]
fn a_grown_up_exports_its_claim_and_pin_and_a_kid_its_limit_and_parent() {
    let (_dir, db) = db();
    let bea = make(&db, "Bea", false);
    let mia = make(&db, "Mia", true);
    let (hash, salt) = ("a".repeat(64), "b".repeat(32));
    sql(&db, &format!(
        "UPDATE profiles SET admin_claimed_at = 7, pin_hash = '{hash}', pin_salt = '{salt}', pin_updated_at = 9 WHERE id = '{bea}';
         UPDATE profiles SET kids_age = 6, kids_age_updated_at = 5, parent_id = '{bea}' WHERE id = '{mia}';"
    ));
    let record = db.with(|c| export_record(c, "tv")).unwrap();
    assert_eq!(
        roles_of(&record, "Bea"),
        ProfileRoles {
            admin: Some(AdminClaim { claimed_at: 7.0 }),
            pin: Some(PinRecord {
                hash,
                salt,
                updated_at: 9.0,
                proven: false,
            }),
            ..Default::default()
        }
    );
    assert_eq!(
        roles_of(&record, "Mia"),
        ProfileRoles {
            kids_age: Some(KidsAge {
                age: 6,
                updated_at: 5.0
            }),
            parent: Some("Bea".into()),
            ..Default::default()
        }
    );
}

#[test]
fn a_kid_with_no_limit_stored_exports_twelve_and_a_parent_gone_is_left_out() {
    let (_dir, db) = db();
    let mia = make(&db, "Mia", true);
    sql(
        &db,
        &format!("UPDATE profiles SET kids_age = NULL, parent_id = 'gone' WHERE id = '{mia}'"),
    );
    let record = db.with(|c| export_record(c, "tv")).unwrap();
    assert_eq!(
        record.profiles[0].roles,
        ProfileRoles {
            kids_age: Some(KidsAge {
                age: 12,
                updated_at: 0.0
            }),
            ..Default::default()
        }
    );
}

#[test]
fn a_limit_is_taken_when_newer_or_as_new_but_different_and_never_when_older() {
    let (_dir, db) = db();
    let mia = make(&db, "Mia", true);
    sql(
        &db,
        &format!("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 10 WHERE id = '{mia}'"),
    );
    let limit = |age: u8, at: f64| {
        viewer(
            "Mia",
            true,
            ProfileRoles {
                kids_age: Some(KidsAge {
                    age,
                    updated_at: at,
                }),
                ..Default::default()
            },
        )
    };
    let age = |db: &StateDb| value::<i64>(db, "SELECT kids_age FROM profiles");
    assert_eq!(import(&db, vec![limit(12, 5.0)]), 0);
    assert_eq!(age(&db), 6);
    assert_eq!(import(&db, vec![limit(12, 10.0)]), 1);
    assert_eq!(age(&db), 12);
    assert_eq!(import(&db, vec![limit(12, 10.0)]), 0);
    assert_eq!(import(&db, vec![limit(6, 11.0)]), 1);
    assert_eq!(age(&db), 6);
}

fn with_pin(p: PinRecord) -> MergedProfile {
    viewer(
        "Bea",
        false,
        ProfileRoles {
            pin: Some(p),
            ..Default::default()
        },
    )
}

#[test]
fn a_proven_pin_is_taken_when_newer_or_as_new_but_different_and_never_when_older() {
    let (_dir, db) = db();
    make(&db, "Bea", false);
    assert_eq!(import(&db, vec![with_pin(proven('c', 5.0))]), 1);
    assert_eq!(import(&db, vec![with_pin(proven('a', 4.0))]), 0);
    assert_eq!(import(&db, vec![with_pin(proven('c', 5.0))]), 0);
    assert_eq!(import(&db, vec![with_pin(proven('d', 5.0))]), 1);
    assert_eq!(
        value::<String>(&db, "SELECT pin_hash FROM profiles"),
        "d".repeat(64)
    );
}

/// The order the merge keeps: a first PIN set later never replaces one set
/// earlier, and no first PIN replaces a proven one — so a kid's tablet that
/// set one for a grown-up it thought had none takes the household's back.
#[test]
fn a_first_pin_is_taken_only_when_older_and_never_over_a_proven_one() {
    let (_dir, db) = db();
    make(&db, "Bea", false);
    assert_eq!(import(&db, vec![with_pin(pin('c', 5.0))]), 1, "none here yet");
    assert_eq!(import(&db, vec![with_pin(pin('a', 6.0))]), 0);
    assert_eq!(import(&db, vec![with_pin(pin('a', 4.0))]), 1);
    assert_eq!(import(&db, vec![with_pin(proven('d', 1.0))]), 1);
    assert_eq!(import(&db, vec![with_pin(pin('e', 0.5))]), 0);
    assert_eq!(
        value::<String>(&db, "SELECT pin_hash FROM profiles"),
        "d".repeat(64)
    );
    let record = db.with(|c| export_record(c, "tv")).unwrap();
    assert!(roles_of(&record, "Bea").pin.unwrap().proven, "a proven PIN goes out proven");
}

#[test]
fn the_merged_admin_is_set_here_and_cleared_on_every_other_profile() {
    let (_dir, db) = db();
    let andre = make(&db, "André", false);
    make(&db, "Bea", false);
    sql(
        &db,
        &format!("UPDATE profiles SET admin_claimed_at = 20 WHERE id = '{andre}'"),
    );
    let claimed = ProfileRoles {
        admin: Some(AdminClaim { claimed_at: 10.0 }),
        ..Default::default()
    };
    assert_eq!(
        import(
            &db,
            vec![
                viewer("André", false, Default::default()),
                viewer("Bea", false, claimed.clone())
            ]
        ),
        2
    );
    assert_eq!(
        value::<i64>(
            &db,
            "SELECT COUNT(*) FROM profiles WHERE admin_claimed_at IS NOT NULL"
        ),
        1
    );
    assert_eq!(
        value::<String>(&db, "SELECT name FROM profiles WHERE admin_claimed_at = 10"),
        "Bea"
    );
    assert_eq!(import(&db, vec![viewer("Bea", false, claimed)]), 0);

    // A merge that names no admin says nothing about one.
    assert_eq!(
        import(&db, vec![viewer("André", false, Default::default())]),
        0
    );
    assert_eq!(
        value::<String>(
            &db,
            "SELECT name FROM profiles WHERE admin_claimed_at IS NOT NULL"
        ),
        "Bea"
    );
}

#[test]
fn a_parent_is_linked_by_name_once_even_when_listed_after_its_kid_and_never_overwritten() {
    let (_dir, db) = db();
    let owned = |parent: &str| {
        viewer(
            "Mia",
            true,
            ProfileRoles {
                parent: Some(parent.into()),
                ..Default::default()
            },
        )
    };
    import(
        &db,
        vec![owned("bea"), viewer("Bea", false, Default::default())],
    );
    let parent = || {
        value::<String>(
            &db,
            "SELECT p.name FROM profiles k JOIN profiles p ON p.id = k.parent_id WHERE k.name = 'Mia'",
        )
    };
    assert_eq!(parent(), "Bea");
    assert_eq!(
        import(
            &db,
            vec![owned("cleo"), viewer("Cleo", false, Default::default())]
        ),
        1,
        "Cleo made, Mia kept"
    );
    assert_eq!(parent(), "Bea");
}

/// A grown-up another device made a kid never reads as a kid with no limit.
#[test]
fn a_grown_up_upgraded_to_a_kid_starts_from_twelve() {
    let (_dir, db) = db();
    make(&db, "Mia", false);
    assert_eq!(
        import(&db, vec![viewer("Mia", true, Default::default())]),
        1
    );
    assert_eq!(value::<i64>(&db, "SELECT kids_age FROM profiles"), 12);
}

/// Each device's document, read back the way another device reads it.
fn exported(db: &StateDb, device: &str) -> SyncRecord {
    let record = db.with(|c| export_record(c, device)).unwrap();
    parse_record(&serde_json::to_string(&record).unwrap()).unwrap()
}

/// Two devices that each made an admin before they met, and a limit a
/// parent set against an older build's bare kid: one round settles both
/// everywhere, and the next round changes nothing.
#[test]
fn two_devices_settle_on_one_admin_and_the_parents_limit_in_one_round() {
    let (_a_dir, laptop) = db();
    let (_b_dir, tv) = db();
    let andre = make(&laptop, "André", false);
    let mia = make(&laptop, "Mia", true);
    sql(&laptop, &format!(
        "UPDATE profiles SET admin_claimed_at = 2000 WHERE id = '{andre}';
         UPDATE profiles SET kids_age = 6, kids_age_updated_at = 5, parent_id = '{andre}' WHERE id = '{mia}';"
    ));
    let bea = make(&tv, "Bea", false);
    make(&tv, "mia", true);
    sql(
        &tv,
        &format!("UPDATE profiles SET admin_claimed_at = 1000 WHERE id = '{bea}'"),
    );

    let docs = [exported(&laptop, "laptop"), exported(&tv, "tv")];
    let merged = merge_states(&docs);
    for db in [&laptop, &tv] {
        assert!(db.with(|c| import_merged(c, &merged)).unwrap() > 0);
        assert_eq!(
            value::<String>(
                db,
                "SELECT name FROM profiles WHERE admin_claimed_at IS NOT NULL"
            ),
            "Bea"
        );
        assert_eq!(
            value::<i64>(db, "SELECT kids_age FROM profiles WHERE kids = 1"),
            6
        );
        assert_eq!(
            value::<String>(
                db,
                "SELECT p.name FROM profiles k JOIN profiles p ON p.id = k.parent_id WHERE k.kids = 1"
            ),
            "André"
        );
    }
    let again = merge_states(&[exported(&laptop, "laptop"), exported(&tv, "tv")]);
    for db in [&laptop, &tv] {
        assert_eq!(
            db.with(|c| import_merged(c, &again)).unwrap(),
            0,
            "a second round is quiet"
        );
    }
}
