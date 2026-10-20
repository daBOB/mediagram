//! Managing profiles inside each household: who a new profile lands beside,
//! what one household's admin cannot touch in another, who may claim a
//! household nobody runs, the owner's actions on households, and joining one.
//! A port of the web's `state-households-manage.test.ts`, case for case.

use super::NewHouseholdAdmin::{self, Existing, New};
use crate::state::exchange::export_record;
use crate::state::households::{Household, device, live};
use crate::state::profiles::ProfileOutcome::{self, *};
use crate::state::profiles::role_rows::{self, Stored};
use crate::state::profiles::{self, Answer, Profile, ProfileManager};
use crate::state::StateDb;

/// The player's clock for every call.
const T: i64 = 1_700_000_000_000;

/// The PIN `pin-hash.json` gives for this salt is 1234.
const PIN: &str = "1234";
const KEY: &str = "'f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924', '00112233445566778899aabbccddeeff'";

/// A grown-up with PIN 1234, in `household`; `claimed` makes them the owner.
fn grown_up(id: &str, household: &str, claimed: bool, created: i64) -> String {
    let claim = if claimed { "1" } else { "NULL" };
    format!(
        "INSERT INTO profiles(id, name, created_at, pin_hash, pin_salt, pin_updated_at, household_id, admin_claimed_at)
           VALUES ('{id}', '{id}', {created}, {KEY}, 1, '{household}', {claim})"
    )
}

fn household(id: &str, admin: Option<&str>) -> String {
    let admin = admin.map_or("NULL".to_string(), |admin| format!("'{admin}'"));
    format!("INSERT INTO households(id, name, admin_id, admin_updated_at) VALUES ('{id}', '{id}', {admin}, 1)")
}

/// A kid of `parent`, stored in `household`.
fn kid(id: &str, parent: &str, household: &str) -> String {
    format!(
        "INSERT INTO profiles(id, name, created_at, kids, kids_age, parent_id, household_id)
           VALUES ('{id}', '{id}', 9, 1, 12, '{parent}', '{household}')"
    )
}

/// A grown-up from before PINs, in `household`.
fn pinless(id: &str, household: &str) -> String {
    format!("INSERT INTO profiles(id, name, created_at, household_id) VALUES ('{id}', '{id}', 5, '{household}')")
}

const GONE: &str = "INSERT INTO households(id, name, removed) VALUES ('gone', 'Loft', 1)";

/// A household's stored row: name, its stamp, admin, its stamp, removed.
type HouseholdRow = (String, i64, Option<String>, i64, bool);

struct Store {
    _dir: tempfile::TempDir,
    db: StateDb,
}

/// A store holding what `rows` writes straight into its file.
fn store_with(rows: &[String]) -> Store {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    db.with(|conn| conn.execute_batch(&rows.join(";\n"))).unwrap();
    Store { _dir: dir, db }
}

impl Store {
    fn run(&self, call: impl FnOnce(&mut ProfileManager<'_>) -> Answer) -> ProfileOutcome {
        self.db.manage_at(T, call)
    }

    /// Every profile, each placed in its household.
    fn everyone(&self) -> Vec<Profile> {
        self.db.with(profiles::list).unwrap()
    }

    fn placed(&self, id: &str) -> Profile {
        self.everyone().into_iter().find(|one| one.id == id).unwrap()
    }

    fn named(&self, name: &str) -> Profile {
        self.everyone().into_iter().find(|one| one.name == name).unwrap()
    }

    fn households(&self) -> Vec<Household> {
        self.db.with(|conn| live(conn, &profiles::list(conn)?)).unwrap()
    }

    fn household_named(&self, name: &str) -> Household {
        self.households().into_iter().find(|one| one.name == name).unwrap()
    }

    fn names(&self) -> Vec<String> {
        self.households().into_iter().map(|one| one.name).collect()
    }

    fn row(&self, id: &str) -> Stored {
        self.db.with(role_rows::load).unwrap().into_iter().find(|row| row.id == id).unwrap()
    }

    fn household_row(&self, id: &str) -> HouseholdRow {
        let query = "SELECT name, name_updated_at, admin_id, admin_updated_at, removed FROM households WHERE id = ?1";
        let read = |r: &rusqlite::Row| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get::<_, i64>(4)? != 0));
        self.db.with(|conn| conn.query_row(query, [id], read)).unwrap()
    }

    fn joined(&self) -> Option<String> {
        self.db.with(device::joined).unwrap()
    }
}

fn new_admin(name: &str, new_pin: &str) -> NewHouseholdAdmin {
    New { name: name.into(), new_pin: new_pin.into() }
}

fn existing(id: &str, new_pin: Option<&str>) -> NewHouseholdAdmin {
    Existing { id: id.into(), new_pin: new_pin.map(Into::into) }
}

// Managing inside a household.

#[test]
fn a_households_admin_adds_a_grown_up_into_its_own_household() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", Some("maja")), grown_up("maja", "flat", false, 2)]);
    assert_eq!(store.run(|m| m.create_grown_up("maja", PIN, "Lena", "5678")), Done);
    assert_eq!(store.named("Lena").household, "flat");
    assert_eq!(store.run(|m| m.create_grown_up("owner", PIN, "Paul", "5678")), Done);
    assert_eq!(store.named("Paul").household, "home");
}

#[test]
fn a_kid_is_made_in_its_parents_household() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", Some("maja")), grown_up("maja", "flat", false, 2)]);
    assert_eq!(store.run(|m| m.create_kid("maja", PIN, "Mia", 6, None)), Done);
    let mia = store.named("Mia");
    assert!(mia.kids);
    assert_eq!(mia.household, "flat");
    assert_eq!(store.row(&mia.id).household_id, "flat", "stored where its parent is, as the web stores it");
}

#[test]
fn the_admin_of_one_household_cannot_remove_a_grown_up_of_another() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", Some("maja")),
        grown_up("maja", "flat", false, 2),
        household("loft", Some("lena")),
        grown_up("lena", "loft", false, 3),
        grown_up("paula", "loft", false, 4),
    ]);
    assert_eq!(store.run(|m| m.remove("maja", PIN, "paula")), NotAllowed);
    assert_eq!(store.placed("paula").household, "loft");
    assert_eq!(store.run(|m| m.remove("lena", PIN, "paula")), Done);
}

// Claiming.

#[test]
fn with_no_owner_yet_the_claim_makes_the_owner_who_runs_home() {
    let store = store_with(&[grown_up("andre", "home", false, 1)]);
    assert_eq!(store.run(|m| m.claim_admin("andre", PIN)), Done);
    let andre = store.placed("andre");
    assert!(andre.owner && andre.admin);
    assert_eq!(andre.household, "home");
}

#[test]
fn a_grown_up_of_a_household_nobody_runs_may_claim_it() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", None), grown_up("maja", "flat", false, 2)]);
    assert!(!store.placed("maja").admin);
    assert_eq!(store.run(|m| m.claim_admin("maja", "0000")), WrongPin);
    assert_eq!(store.run(|m| m.claim_admin("maja", PIN)), Done);
    let maja = store.placed("maja");
    assert!(maja.admin && !maja.owner);
    assert!(store.placed("owner").admin);
    assert_eq!(store.household_row("flat").2.as_deref(), Some("maja"));
    assert!(store.household_row("flat").3 > 1, "the claim is dated");
}

#[test]
fn a_grown_up_with_no_pin_claims_with_the_first_one_given() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", None), pinless("maja", "flat")]);
    assert_eq!(store.run(|m| m.claim_admin("maja", "")), Invalid);
    assert_eq!(store.run(|m| m.claim_admin("maja", "5678")), Done);
    assert_eq!(store.run(|m| m.unlock("maja", "5678")), Done);
    assert!(store.placed("maja").admin);
}

#[test]
fn nobody_claims_a_household_somebody_runs() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", Some("maja")),
        grown_up("maja", "flat", false, 2),
        grown_up("lena", "flat", false, 3),
    ]);
    assert_eq!(store.run(|m| m.claim_admin("lena", PIN)), NotAllowed);
    assert_eq!(store.run(|m| m.claim_admin("owner", PIN)), NotAllowed);
    assert!(store.placed("maja").admin);
}

// The owner's actions.

#[test]
fn the_owner_creates_a_household_with_a_new_grown_up_who_runs_it() {
    let store = store_with(&[grown_up("owner", "home", true, 1)]);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, " Flat ", &new_admin("Maja", "5678"))), Done);
    let maja = store.named("Maja");
    let made = store.household_named("Flat");
    assert_eq!(made.admin_id.as_deref(), Some(maja.id.as_str()));
    assert_eq!(maja.household, made.id);
    assert!(maja.admin && maja.has_pin);
    assert_eq!(store.run(|m| m.unlock(&maja.id, "5678")), Done);
    assert_eq!(store.names(), ["Flat", "Home"]);
}

#[test]
fn the_owner_creates_one_with_an_existing_grown_up_who_moves_in_with_their_kids() {
    let store = store_with(&[grown_up("owner", "home", true, 1), grown_up("paul", "home", false, 2), kid("mia", "paul", "home")]);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &existing("paul", None))), Done);
    let made = store.household_named("Loft");
    assert_eq!(made.admin_id.as_deref(), Some("paul"));
    assert_eq!(store.placed("paul").household, made.id);
    assert!(store.placed("paul").admin);
    assert_eq!(store.placed("mia").household, made.id);
    for id in ["paul", "mia"] {
        let row = store.row(id);
        assert_eq!(row.household_id, made.id);
        assert!(row.household_updated_at > 0, "{id} is dated");
    }
}

#[test]
fn a_household_made_here_is_said_with_its_name() {
    let store = store_with(&[grown_up("owner", "home", true, 1)]);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Flat", &new_admin("Maja", "5678"))), Done);
    let made = store.household_named("Flat");
    let record = store.db.with(|conn| export_record(conn, "laptop")).unwrap();
    let said = record.households.into_iter().find(|one| one.id == made.id).unwrap();
    let (name, admin) = (said.name.unwrap(), said.admin.unwrap());
    assert_eq!(name.text, "Flat");
    assert!(name.updated_at > 0.0);
    assert_eq!(admin.name, "Maja");
    assert!(admin.updated_at > 0.0);
}

#[test]
fn a_households_name_is_unique_among_the_live_ones() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", None), GONE.into()]);
    let maja = || new_admin("Maja", "5678");
    assert_eq!(store.run(|m| m.create_household("owner", PIN, " FLAT", &maja())), NameTaken);
    assert_eq!(store.run(|m| m.rename_household("owner", PIN, "home", "flat")), NameTaken);
    assert_eq!(store.run(|m| m.rename_household("owner", PIN, "flat", "Flat")), Done);
    // A deleted household's name is free again.
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &maja())), Done);
    assert!(store.households().iter().any(|one| one.name == "Loft" && one.id != "gone"));
    assert_eq!(store.run(|m| m.rename_household("owner", PIN, "home", "  ")), Invalid);
    assert_eq!(store.run(|m| m.rename_household("owner", PIN, "home", "Borck")), Done);
    let (name, stamp, ..) = store.household_row("home");
    assert_eq!(name, "Borck");
    assert!(stamp > 0);
}

#[test]
fn a_new_grown_ups_name_is_unique_on_the_whole_install() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", Some("lena")), grown_up("lena", "flat", false, 2)]);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &new_admin(" LENA ", "5678"))), NameTaken);
    assert_eq!(store.names(), ["flat", "Home"]);
}

#[test]
fn a_new_household_admin_may_not_take_a_name_another_profile_had_before() {
    let store = store_with(&[grown_up("owner", "home", true, 1), "UPDATE profiles SET former_names = '[\"mia\"]' WHERE id = 'owner'".to_string()]);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &new_admin("Mia", "5678"))), NameTaken);
    assert_eq!(store.names(), ["Home"]);
}

#[test]
fn an_admin_without_a_pin_is_given_one_and_refused_without() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        pinless("paul", "home"),
        household("flat", None),
        pinless("lena", "flat"),
    ]);
    // A new grown-up always comes with a PIN across the boundary: an empty
    // one is a PIN that is not one, as the web answers an empty `newPin`.
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &new_admin("Maja", ""))), Invalid);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &new_admin("Maja", "12"))), Invalid);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &existing("paul", None))), NoPin);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &existing("paul", Some("5678")))), Done);
    assert_eq!(store.household_named("Loft").admin_id.as_deref(), Some("paul"));
    assert_eq!(store.run(|m| m.unlock("paul", "5678")), Done);
    assert_eq!(store.run(|m| m.set_household_admin("owner", PIN, "flat", "lena", None)), NoPin);
    assert_eq!(store.run(|m| m.set_household_admin("owner", PIN, "flat", "lena", Some("x"))), Invalid);
    assert!(!store.placed("lena").admin);
    assert_eq!(store.run(|m| m.set_household_admin("owner", PIN, "flat", "lena", Some("4321"))), Done);
    assert!(store.placed("lena").admin);
    assert_eq!(store.run(|m| m.unlock("lena", "4321")), Done);
    assert!(store.household_row("flat").3 > 1);
}

#[test]
fn the_owner_moves_a_grown_up_and_their_kids_each_dated() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", Some("maja")),
        grown_up("maja", "flat", false, 2),
        grown_up("paul", "home", false, 3),
        kid("mia", "paul", "home"),
    ]);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "paul", None)), Done);
    for id in ["paul", "mia"] {
        assert_eq!(store.placed(id).household, "flat");
        assert!(store.row(id).household_updated_at > 0, "{id} is dated");
    }
    // The household already had someone running it.
    assert!(store.placed("maja").admin);
    assert!(!store.placed("paul").admin);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "paul", None)), NotAllowed);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "mia", None)), NotAllowed);
}

#[test]
fn a_grown_up_moved_into_a_household_nobody_runs_runs_it() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", None), grown_up("paul", "home", false, 3)]);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "paul", None)), Done);
    let paul = store.placed("paul");
    assert_eq!(paul.household, "flat");
    assert!(paul.admin);
    let (_, _, admin, admin_at, _) = store.household_row("flat");
    assert_eq!(admin.as_deref(), Some("paul"));
    assert!(admin_at > 1);
}

#[test]
fn a_grown_up_moved_in_to_run_a_household_is_given_a_first_pin_and_refused_without() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", None), pinless("lena", "home")]);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "lena", None)), NoPin);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "lena", Some("12"))), Invalid);
    assert_eq!(store.placed("lena").household, "home");
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "lena", Some("4321"))), Done);
    let lena = store.placed("lena");
    assert_eq!(lena.household, "flat");
    assert!(lena.admin && lena.has_pin);
    assert_eq!(store.run(|m| m.unlock("lena", "4321")), Done);
}

#[test]
fn a_moves_pin_changes_nothing_where_nobody_is_made_admin_or_the_mover_has_one() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", Some("maja")),
        grown_up("maja", "flat", false, 2),
        household("loft", None),
        grown_up("paul", "home", false, 3),
        pinless("lena", "home"),
    ]);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "lena", Some("4321"))), Done);
    let lena = store.placed("lena");
    assert_eq!(lena.household, "flat");
    assert!(!lena.admin && !lena.has_pin);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "loft", "paul", Some("4321"))), Done);
    let paul = store.placed("paul");
    assert_eq!(paul.household, "loft");
    assert!(paul.admin);
    assert_eq!(store.run(|m| m.unlock("paul", PIN)), Done);
}

#[test]
fn a_new_pin_for_an_admin_to_be_who_has_one_changes_nothing() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        grown_up("paul", "home", false, 2),
        household("flat", None),
        grown_up("lena", "flat", false, 3),
    ]);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &existing("paul", Some("4321")))), Done);
    assert_eq!(store.household_named("Loft").admin_id.as_deref(), Some("paul"));
    assert_eq!(store.run(|m| m.unlock("paul", PIN)), Done);
    assert_eq!(store.run(|m| m.set_household_admin("owner", PIN, "flat", "lena", Some("4321"))), Done);
    assert_eq!(store.run(|m| m.unlock("lena", PIN)), Done);
}

#[test]
fn a_households_admin_is_one_of_its_own_grown_ups() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", None),
        grown_up("lena", "flat", false, 2),
        grown_up("paul", "home", false, 3),
    ]);
    assert_eq!(store.run(|m| m.set_household_admin("owner", PIN, "flat", "paul", None)), NotAllowed);
    let paul = store.placed("paul");
    assert_eq!(paul.household, "home");
    assert!(!paul.admin);
    assert_eq!(store.household_row("flat").2, None);
}

#[test]
fn a_households_last_grown_up_moves_out_and_the_emptied_household_can_be_deleted() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", Some("maja")),
        grown_up("maja", "flat", false, 2),
        grown_up("lena", "flat", false, 3),
    ]);
    // Its admin stays while others remain; the owner changes the admin first.
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "home", "maja", None)), NotAllowed);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "home", "lena", None)), Done);
    assert_eq!(store.run(|m| m.delete_household("owner", PIN, "flat")), NotAllowed);
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "home", "maja", None)), Done);
    let maja = store.placed("maja");
    assert_eq!(maja.household, "home");
    assert!(!maja.admin);
    assert_eq!(store.run(|m| m.delete_household("owner", PIN, "flat")), Done);
    let ids: Vec<String> = store.households().into_iter().map(|one| one.id).collect();
    assert_eq!(ids, ["home"]);
}

#[test]
fn the_owner_deletes_an_empty_household_never_home() {
    let store = store_with(&[household("flat", Some("owner")), grown_up("owner", "flat", true, 1), household("loft", None)]);
    assert_eq!(store.run(|m| m.delete_household("owner", PIN, "home")), NotAllowed);
    assert_eq!(store.run(|m| m.delete_household("owner", PIN, "loft")), Done);
    assert!(store.household_row("loft").4, "removed for good");
    assert_eq!(store.run(|m| m.delete_household("owner", PIN, "loft")), NotFound);
    assert_eq!(store.run(|m| m.rename_household("owner", PIN, "loft", "Again")), NotFound);
    let ids: Vec<String> = store.households().into_iter().map(|one| one.id).collect();
    assert_eq!(ids, ["flat", "home"]);
}

#[test]
fn only_the_owner_acts_on_households() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", Some("maja")),
        grown_up("maja", "flat", false, 2),
        kid("mia", "maja", "flat"),
    ]);
    assert_eq!(store.run(|m| m.create_household("maja", PIN, "Loft", &new_admin("Paul", "5678"))), NotAllowed);
    assert_eq!(store.run(|m| m.rename_household("maja", PIN, "flat", "Mine")), NotAllowed);
    assert_eq!(store.run(|m| m.rename_household("mia", PIN, "flat", "Mine")), NotAllowed);
    assert_eq!(store.run(|m| m.rename_household("owner", "0000", "flat", "Mine")), WrongPin);
    assert_eq!(store.run(|m| m.rename_household("nobody", PIN, "flat", "Mine")), NotFound);
    assert_eq!(store.run(|m| m.set_household_admin("owner", PIN, "flat", "nobody", Some("5678"))), NotFound);
    // The owner is never moved, and never made another household's admin.
    assert_eq!(store.run(|m| m.move_to_household("owner", PIN, "flat", "owner", None)), NotAllowed);
    assert_eq!(store.run(|m| m.create_household("owner", PIN, "Loft", &existing("owner", None))), NotAllowed);
    assert_eq!(store.run(|m| m.set_household_admin("owner", PIN, "home", "owner", None)), NotAllowed);
    assert_eq!(store.names(), ["flat", "Home"]);
}

// Joining.

#[test]
fn joining_takes_the_household_admins_pin_or_the_owners_and_remembers_it() {
    let store = store_with(&[
        grown_up("owner", "home", true, 1),
        household("flat", Some("maja")),
        grown_up("maja", "flat", false, 2),
        grown_up("lena", "flat", false, 3),
        household("loft", None),
        "INSERT INTO households(id, name, removed) VALUES ('gone', 'Gone', 1)".into(),
    ]);
    assert_eq!(store.run(|m| m.set_pin("maja", PIN, "maja", "5678")), Done);
    assert_eq!(store.run(|m| m.set_pin("lena", PIN, "lena", "4321")), Done);
    assert_eq!(store.run(|m| m.join_household("flat", "5678")), Done);
    assert_eq!(store.joined().as_deref(), Some("flat"), "this device now shows the household it joined");
    assert_eq!(store.run(|m| m.join_household("flat", PIN)), Done);
    assert_eq!(store.run(|m| m.join_household("flat", "4321")), WrongPin);
    assert_eq!(store.run(|m| m.join_household("loft", PIN)), Done);
    assert_eq!(store.run(|m| m.join_household("home", "5678")), WrongPin);
    assert_eq!(store.run(|m| m.join_household("gone", PIN)), NotFound);
    assert_eq!(store.run(|m| m.join_household("nowhere", PIN)), NotFound);
    assert_eq!(store.joined().as_deref(), Some("loft"), "a refused join moves nothing");
}

#[test]
fn a_household_nobody_with_a_pin_runs_cannot_be_joined() {
    let store = store_with(&[pinless("andre", "home"), household("loft", None)]);
    assert_eq!(store.run(|m| m.join_household("loft", PIN)), NoPin);
}

#[test]
fn wrong_join_pins_wait_per_household_not_per_profile() {
    let store = store_with(&[grown_up("owner", "home", true, 1), household("flat", Some("maja")), grown_up("maja", "flat", false, 2)]);
    for _ in 0..5 {
        assert_eq!(store.run(|m| m.join_household("flat", "0000")), WrongPin);
    }
    assert_eq!(store.run(|m| m.join_household("flat", PIN)), Wait { seconds: 60 });
    // Another household's door, and the profiles themselves, are not held.
    assert_eq!(store.run(|m| m.join_household("home", PIN)), Done);
    assert_eq!(store.run(|m| m.unlock("owner", PIN)), Done);
    assert_eq!(store.run(|m| m.unlock("maja", PIN)), Done);
}
