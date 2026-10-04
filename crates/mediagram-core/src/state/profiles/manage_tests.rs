//! Managing profiles, PIN and rule checked: every operation's answer, and
//! the order the refusals are checked in. A port of the web's
//! `state-profiles-manage.test.ts`, case for case where the two surfaces
//! share the behaviour.

use rusqlite::types::FromSql;

use super::ProfileManager;
use crate::state::profiles::ProfileOutcome::{self, *};
use crate::state::StateDb;
use crate::state::exchange::import_merged;
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::profiles::create;
use crate::state::profiles::role_rows::{self, Stored};
use crate::state::record::{KidsAge, MAX_STAMP, PinRecord, ProfileRoles};

/// The player's clock for every call that does not say otherwise.
const T: i64 = 1_700_000_000_000;

/// The PIN `pin-hash.json` gives for this salt is 1234.
const PIN_1234_HASH: &str = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
const PIN_1234_SALT: &str = "00112233445566778899aabbccddeeff";

struct Home {
    _dir: tempfile::TempDir,
    db: StateDb,
}

type Call<'c> = Box<dyn FnOnce(&mut ProfileManager<'_>) -> rusqlite::Result<ProfileOutcome> + 'c>;

impl Home {
    /// A player that has taken in one sync round, which found nobody — a new
    /// household, free to make its first profile.
    fn new() -> Self {
        let home = Self::unsynced();
        home.import(Vec::new());
        home
    }

    /// A player that has never finished a sync round.
    fn unsynced() -> Self {
        let dir = tempfile::tempdir().unwrap();
        let db = StateDb::new(dir.path().to_path_buf());
        Home { _dir: dir, db }
    }

    /// One management call with the player's clock at `now`.
    fn at(&self, now: i64, call: Call) -> ProfileOutcome {
        self.db.manage_at(now, call)
    }

    fn run(&self, call: Call) -> ProfileOutcome {
        self.at(T, call)
    }

    /// The app swiped away and opened again: this store's connection closed,
    /// a new owner over the same directory.
    fn restart(&mut self) {
        self.db.retire();
        self.db = StateDb::new(self._dir.path().to_path_buf());
    }

    /// A profile the way sync makes one: no PIN, no claim, no parent.
    fn synced(&self, name: &str, kids: bool) -> String {
        self.db.with(|c| create(c, name, kids)).unwrap().unwrap().id
    }

    fn rows(&self) -> Vec<Stored> {
        self.db.with(role_rows::load).unwrap()
    }

    fn row(&self, id: &str) -> Option<Stored> {
        self.rows().into_iter().find(|row| row.id == id)
    }

    fn named(&self, name: &str) -> Stored {
        self.rows()
            .into_iter()
            .find(|row| row.name == name)
            .unwrap()
    }

    fn column<V: FromSql>(&self, column: &str, id: &str) -> V {
        let query = format!("SELECT {column} FROM profiles WHERE id = ?1");
        self.db
            .with(|c| c.query_row(&query, [id], |r| r.get(0)))
            .unwrap()
    }

    /// A kid's limit as stored.
    fn limit(&self, id: &str) -> Option<i64> {
        self.column("kids_age", id)
    }

    fn import(&self, profiles: Vec<MergedProfile>) {
        let merged = MergedState {
            profiles,
            ..Default::default()
        };
        self.db.with(|c| import_merged(c, &merged)).unwrap();
    }

    /// Five wrong PINs: `id`'s PIN is not compared for a minute from `T`.
    fn start_wait(&self, id: &str) {
        for _ in 0..5 {
            assert_eq!(self.run(unlock(id, "0000")), WrongPin);
        }
    }
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

fn pin_1234(updated_at: f64) -> ProfileRoles {
    let pin = PinRecord {
        hash: PIN_1234_HASH.into(),
        salt: PIN_1234_SALT.into(),
        updated_at,
    };
    ProfileRoles {
        pin: Some(pin),
        ..Default::default()
    }
}

// One boxed call per operation, so a test reads as the web's does.
fn first<'c>(name: &'c str, new_pin: &'c str) -> Call<'c> {
    Box::new(move |m| m.create_first(name, new_pin))
}
fn grown_up<'c>(actor: &'c str, pin: &'c str, name: &'c str, new_pin: &'c str) -> Call<'c> {
    Box::new(move |m| m.create_grown_up(actor, pin, name, new_pin))
}
fn kid<'c>(actor: &'c str, pin: &'c str, name: &'c str, age: u8) -> Call<'c> {
    Box::new(move |m| m.create_kid(actor, pin, name, age))
}
fn remove<'c>(actor: &'c str, pin: &'c str, id: &'c str) -> Call<'c> {
    Box::new(move |m| m.remove(actor, pin, id))
}
fn kids_age<'c>(actor: &'c str, pin: &'c str, id: &'c str, age: u8) -> Call<'c> {
    Box::new(move |m| m.set_kids_age(actor, pin, id, age))
}
fn unlock<'c>(id: &'c str, pin: &'c str) -> Call<'c> {
    Box::new(move |m| m.unlock(id, pin))
}
fn claim<'c>(id: &'c str, pin: &'c str) -> Call<'c> {
    Box::new(move |m| m.claim_admin(id, pin))
}
fn set_pin<'c>(actor: &'c str, pin: &'c str, id: &'c str, new_pin: &'c str) -> Call<'c> {
    Box::new(move |m| m.set_pin(actor, pin, id, new_pin))
}

/// André the admin (PIN 1111), Maja a parent (2222), her kid Mia (12),
/// André's kid Leo (6).
struct Household {
    home: Home,
    andre: String,
    maja: String,
    mia: String,
    leo: String,
}

fn household() -> Household {
    let home = Home::new();
    assert_eq!(home.run(first("André", "1111")), Done);
    let andre = home.named("André").id;
    assert_eq!(home.run(grown_up(&andre, "1111", "Maja", "2222")), Done);
    let maja = home.named("Maja").id;
    assert_eq!(home.run(kid(&maja, "2222", "Mia", 12)), Done);
    assert_eq!(home.run(kid(&andre, "1111", "Leo", 6)), Done);
    let (mia, leo) = (home.named("Mia").id, home.named("Leo").id);
    Household {
        home,
        andre,
        maja,
        mia,
        leo,
    }
}

mod the_first_profile {
    use super::*;

    #[test]
    fn a_player_with_no_grown_up_makes_one_and_it_runs_the_household() {
        let home = Home::new();
        assert_eq!(home.run(first("André", "1111")), Done);
        let andre = home.named("André");
        assert!(!andre.kids && andre.is_admin() && andre.pin_hash.is_some());
        // Claimed, PIN-stamped and made in the same moment.
        assert_eq!(home.column::<i64>("admin_claimed_at", &andre.id), T);
        assert_eq!(home.column::<i64>("pin_updated_at", &andre.id), T);
        assert_eq!(home.column::<i64>("created_at", &andre.id), T);
        assert_eq!(home.run(unlock(&andre.id, "1111")), Done);
    }

    #[test]
    fn only_while_no_grown_up_exists_and_kids_alone_do_not_count() {
        let home = Home::new();
        home.synced("TV kids", true);
        assert_eq!(home.run(first("André", "1111")), Done);
        assert_eq!(home.run(first("Maja", "2222")), NotAllowed);
    }

    #[test]
    fn a_grown_up_from_before_pins_counts_so_that_household_claims_instead() {
        let home = Home::new();
        home.synced("Sam", false);
        assert_eq!(home.run(first("André", "1111")), NotAllowed);
    }

    #[test]
    fn its_name_and_pin_have_to_be_usable() {
        let home = Home::new();
        assert_eq!(home.run(first("  ", "1111")), Invalid);
        assert_eq!(home.run(first("André", "11")), Invalid);
        assert_eq!(home.run(first("André", "")), Invalid);
        assert!(home.rows().is_empty());
    }

    /// A player that has not heard from the household cannot know whether
    /// "André" is already somebody's name there; made blind, the newer PIN
    /// would become that grown-up's on every device once the two met.
    #[test]
    fn waits_until_this_player_has_taken_in_a_sync_round() {
        let home = Home::unsynced();
        assert_eq!(home.run(first("André", "1111")), NotSynced);
        assert!(home.rows().is_empty());
        // What cannot be used is still said first.
        assert_eq!(home.run(first("André", "11")), Invalid);
    }

    #[test]
    fn a_round_that_found_nobody_lets_a_new_household_begin() {
        let home = Home::unsynced();
        home.import(Vec::new());
        assert_eq!(home.run(first("André", "1111")), Done);
        assert!(home.named("André").is_admin());
    }

    #[test]
    fn a_round_that_brought_the_households_grown_ups_makes_nobody_first() {
        let home = Home::unsynced();
        home.import(vec![viewer("André", false, pin_1234(T as f64))]);
        assert_eq!(home.run(first("André", "1111")), NameTaken);
        assert_eq!(home.run(first("Mallory", "0000")), NotAllowed);
        assert_eq!(home.rows().len(), 1);
    }

    #[test]
    fn a_restart_remembers_the_round() {
        let mut home = Home::new();
        home.restart();
        assert_eq!(home.run(first("André", "1111")), Done);
    }

    /// Nothing on this surface throws: a store that cannot be written answers
    /// `Invalid`, the value that means nothing happened.
    #[test]
    fn a_player_that_remembers_nothing_makes_nobody() {
        let home = Home::new();
        home.db.retire();
        assert_eq!(home.run(first("André", "1111")), Invalid);
    }
}

mod claiming_the_admin {
    use super::*;

    #[test]
    fn the_first_grown_up_to_claim_is_the_admin_and_the_pin_given_becomes_theirs() {
        let home = Home::new();
        let andre = home.synced("André", false);
        assert_eq!(home.run(claim(&andre, "1111")), Done);
        let row = home.row(&andre).unwrap();
        assert!(row.is_admin() && row.pin_hash.is_some());
        assert_eq!(home.column::<i64>("admin_claimed_at", &andre), T);
        assert_eq!(home.run(unlock(&andre, "1111")), Done);
    }

    #[test]
    fn there_is_only_ever_one() {
        let home = Home::new();
        let andre = home.synced("André", false);
        let maja = home.synced("Maja", false);
        assert_eq!(home.run(claim(&andre, "1111")), Done);
        assert_eq!(home.run(claim(&maja, "2222")), NotAllowed);
        assert_eq!(home.run(claim(&andre, "1111")), NotAllowed);
        // Refused, so the PIN it offered was not kept either.
        let row = home.row(&maja).unwrap();
        assert!(!row.is_admin() && row.pin_hash.is_none());
    }

    #[test]
    fn once_there_is_one_a_claim_is_refused_before_any_pin_is_compared() {
        let home = Home::new();
        let andre = home.synced("André", false);
        let maja = home.synced("Maja", false);
        assert_eq!(home.run(set_pin(&maja, "", &maja, "2222")), Done);
        assert_eq!(home.run(claim(&andre, "1111")), Done);
        for _ in 0..5 {
            assert_eq!(home.run(claim(&maja, "9999")), NotAllowed);
        }
        assert_eq!(
            home.run(unlock(&maja, "2222")),
            Done,
            "no wrong PIN was counted"
        );
    }

    #[test]
    fn a_grown_up_with_a_pin_must_give_it_and_a_kid_cannot_claim() {
        let home = Home::new();
        let maja = home.synced("Maja", false);
        let mia = home.synced("Mia", true);
        assert_eq!(home.run(set_pin(&maja, "", &maja, "2222")), Done);
        assert_eq!(home.run(claim(&maja, "9999")), WrongPin);
        assert_eq!(home.run(claim(&mia, "1234")), NotAllowed);
        assert_eq!(home.run(claim(&maja, "2222")), Done);
    }

    #[test]
    fn the_pin_a_grown_up_without_one_would_take_has_to_be_four_digits() {
        let home = Home::new();
        let andre = home.synced("André", false);
        assert_eq!(home.run(claim(&andre, "12345")), Invalid);
        assert_eq!(home.run(claim("nobody", "1111")), NotFound);
        assert_eq!(home.run(claim("nobody", "12")), NotFound);
    }

    #[test]
    fn a_claim_waits_only_when_it_would_compare_a_pin() {
        let home = Home::new();
        let maja = home.synced("Maja", false);
        let sam = home.synced("Sam", false);
        assert_eq!(home.run(set_pin(&maja, "", &maja, "2222")), Done);
        home.start_wait(&maja);
        assert_eq!(home.run(claim(&maja, "2222")), Wait { seconds: 60 });
        assert_eq!(home.run(claim(&sam, "3333")), Done);
    }

    #[test]
    fn an_admin_another_device_later_called_a_kid_runs_nothing_so_a_grown_up_may_claim() {
        let home = Home::new();
        assert_eq!(home.run(first("André", "1111")), Done);
        let andre = home.named("André").id;
        let maja = home.synced("Maja", false);
        // Another device's document says André is a kid; `kids` only ever turns on.
        home.import(vec![viewer("André", true, ProfileRoles::default())]);
        let row = home.row(&andre).unwrap();
        assert!(row.kids && !row.is_admin());
        assert_eq!(home.run(unlock(&andre, "")), Done);
        assert_eq!(home.run(claim(&maja, "2222")), Done);
        assert!(home.row(&maja).unwrap().is_admin());
    }
}

mod entering_a_profile {
    use super::*;

    #[test]
    fn a_kids_opens_without_a_pin() {
        let home = Home::new();
        let mia = home.synced("Mia", true);
        assert_eq!(home.run(unlock(&mia, "")), Done);
    }

    #[test]
    fn a_grown_ups_opens_with_its_pin_only() {
        let home = Home::new();
        let andre = home.synced("André", false);
        assert_eq!(home.run(claim(&andre, "1111")), Done);
        assert_eq!(home.run(unlock(&andre, "1112")), WrongPin);
        assert_eq!(home.run(unlock(&andre, "1111")), Done);
        // Not four digits is not a malformed request, only a wrong PIN.
        assert_eq!(home.run(unlock(&andre, "11a1")), WrongPin);
        assert_eq!(home.run(unlock("nobody", "1111")), NotFound);
    }

    #[test]
    fn a_grown_up_from_before_pins_has_none_to_give_yet() {
        let home = Home::new();
        let sam = home.synced("Sam", false);
        assert_eq!(home.run(unlock(&sam, "1234")), NoPin);
    }

    #[test]
    fn a_pin_set_on_another_device_opens_the_profile_here() {
        let home = Home::new();
        let sam = home.synced("Sam", false);
        home.import(vec![viewer("Sam", false, pin_1234(5.0))]);
        assert_eq!(home.run(unlock(&sam, "1234")), Done);
    }
}

mod a_grown_ups_pin {
    use super::*;

    #[test]
    fn a_grown_up_from_before_pins_sets_its_first_one_with_nothing_to_prove() {
        let home = Home::new();
        let sam = home.synced("Sam", false);
        assert_eq!(home.run(set_pin(&sam, "", &sam, "4444")), Done);
        assert_eq!(home.run(unlock(&sam, "4444")), Done);
        // From then on, changing it asks for it.
        assert_eq!(home.run(set_pin(&sam, "", &sam, "5555")), WrongPin);
        assert_eq!(home.run(set_pin(&sam, "4444", &sam, "5555")), Done);
        assert_eq!(home.run(unlock(&sam, "5555")), Done);
    }

    #[test]
    fn the_admin_resets_another_grown_ups_and_a_grown_up_cannot_reset_the_admins() {
        let home = Home::new();
        let andre = home.synced("André", false);
        let maja = home.synced("Maja", false);
        assert_eq!(home.run(claim(&andre, "1111")), Done);
        assert_eq!(home.run(set_pin(&maja, "", &maja, "2222")), Done);
        assert_eq!(home.run(set_pin(&andre, "1111", &maja, "3333")), Done);
        assert_eq!(home.run(unlock(&maja, "2222")), WrongPin);
        assert_eq!(home.run(unlock(&maja, "3333")), Done);
        assert_eq!(home.run(set_pin(&maja, "3333", &andre, "0000")), NotAllowed);
    }

    #[test]
    fn a_kid_has_none_and_a_new_pin_must_be_four_digits() {
        let home = Home::new();
        let andre = home.synced("André", false);
        let mia = home.synced("Mia", true);
        assert_eq!(home.run(claim(&andre, "1111")), Done);
        assert_eq!(home.run(set_pin(&andre, "1111", &mia, "3333")), NotAllowed);
        assert_eq!(home.run(set_pin(&andre, "1111", &andre, "33")), Invalid);
    }

    #[test]
    fn a_new_pin_outdates_one_another_devices_clock_stamped_however_far_ahead() {
        let home = Home::new();
        let sam = home.synced("Sam", false);
        let ahead = T + 60_000;
        home.import(vec![viewer("Sam", false, pin_1234(ahead as f64))]);
        assert_eq!(home.run(set_pin(&sam, "1234", &sam, "5555")), Done);
        assert_eq!(home.column::<i64>("pin_updated_at", &sam), ahead + 1);
    }

    #[test]
    fn a_new_pin_over_one_stamped_at_the_last_safe_time_stays_a_time_every_peer_keeps() {
        let home = Home::new();
        let sam = home.synced("Sam", false);
        home.import(vec![viewer("Sam", false, pin_1234(MAX_STAMP))]);
        assert_eq!(home.run(set_pin(&sam, "1234", &sam, "5555")), Done);
        assert_eq!(home.column::<i64>("pin_updated_at", &sam), MAX_STAMP as i64);
    }

    #[test]
    fn every_pin_write_gets_its_own_salt() {
        let Household {
            home, andre, maja, ..
        } = household();
        let before: String = home.column("pin_salt", &maja);
        assert_eq!(home.run(set_pin(&andre, "1111", &maja, "2222")), Done);
        assert_ne!(home.column::<String>("pin_salt", &maja), before);
        assert_eq!(home.run(unlock(&maja, "2222")), Done);
    }
}

mod adding_to_the_household {
    use super::*;

    #[test]
    fn the_admin_adds_a_grown_up_with_a_first_pin_and_a_grown_up_adds_its_own_kid() {
        let Household {
            home,
            andre,
            maja,
            mia,
            leo,
        } = household();
        let made = home.row(&maja).unwrap();
        assert!(!made.kids && !made.is_admin() && made.pin_hash.is_some());
        assert_eq!(home.column::<i64>("pin_updated_at", &maja), T);
        assert_eq!(home.run(unlock(&maja, "2222")), Done);
        let mia = home.row(&mia).unwrap();
        assert_eq!(
            (mia.kids, home.limit(&mia.id), mia.parent_id.as_deref()),
            (true, Some(12), Some(maja.as_str()))
        );
        assert!(mia.pin_hash.is_none());
        let leo = home.row(&leo).unwrap();
        assert_eq!(
            (leo.kids, home.limit(&leo.id), leo.parent_id.as_deref()),
            (true, Some(6), Some(andre.as_str()))
        );
    }

    /// A limit chosen here is dated when the kid is made, so a default synced
    /// in later — FSK 12 dated 0 — cannot undo it.
    #[test]
    fn a_chosen_limit_is_dated_so_a_default_synced_in_later_cannot_undo_it() {
        let Household { home, leo, .. } = household();
        assert_eq!(home.column::<i64>("kids_age_updated_at", &leo), T);
        let default = KidsAge {
            age: 12,
            updated_at: 0.0,
        };
        home.import(vec![viewer(
            "Leo",
            true,
            ProfileRoles {
                kids_age: Some(default),
                ..Default::default()
            },
        )]);
        assert_eq!(home.limit(&leo), Some(6));
    }

    #[test]
    fn only_the_admin_adds_a_grown_up() {
        let Household { home, maja, .. } = household();
        assert_eq!(home.run(grown_up(&maja, "2222", "Ben", "3333")), NotAllowed);
    }

    #[test]
    fn a_kid_adds_nobody() {
        let Household { home, mia, .. } = household();
        assert_eq!(home.run(kid(&mia, "0000", "Zoe", 6)), NotAllowed);
    }

    #[test]
    fn a_name_a_new_pin_and_a_limit_have_to_be_usable_before_anything_else_is_asked() {
        let Household { home, andre, .. } = household();
        assert_eq!(home.run(grown_up(&andre, "1111", "   ", "3333")), Invalid);
        assert_eq!(home.run(grown_up(&andre, "1111", "Ben", "33")), Invalid);
        assert_eq!(home.run(grown_up(&andre, "1111", "Ben", "")), Invalid);
        assert_eq!(home.run(kid(&andre, "1111", "Zoe", 9)), Invalid);
        assert_eq!(home.run(kid(&andre, "1111", "Zoe", 0)), Invalid);
        assert_eq!(home.run(kid("nobody", "1111", "Zoe", 9)), Invalid);
        assert_eq!(home.rows().len(), 4);
    }
}

mod a_name_already_here {
    use super::*;

    #[test]
    fn is_refused_for_a_kid_a_grown_up_and_a_first_profile_however_it_is_spelled() {
        let Household {
            home, andre, maja, ..
        } = household();
        assert_eq!(home.run(kid(&maja, "2222", "ANDRÉ ", 6)), NameTaken);
        assert_eq!(
            home.run(grown_up(&andre, "1111", "  maja", "3333")),
            NameTaken
        );
        assert_eq!(home.run(kid(&andre, "1111", "leo", 6)), NameTaken);
        assert_eq!(home.rows().len(), 4);

        let kids_only = Home::new();
        kids_only.synced("Andre", true);
        assert_eq!(kids_only.run(first("andre", "1111")), NameTaken);
        assert_eq!(kids_only.run(first("André", "1111")), Done);
    }

    /// Sync knows a viewer by its name and only ever turns `kids` on: a kid
    /// called "andré" would have made the admin a kid on every device.
    #[test]
    fn so_a_kid_can_never_take_the_admins_name_and_with_it_the_admins_place() {
        let Household {
            home, andre, maja, ..
        } = household();
        assert_eq!(home.run(kid(&maja, "2222", "andré", 12)), NameTaken);
        let row = home.row(&andre).unwrap();
        assert!(!row.kids && row.is_admin());
    }

    #[test]
    fn is_said_right_after_a_name_that_cannot_be_used_before_nobody_there_and_before_the_wait() {
        let Household { home, maja, .. } = household();
        home.start_wait(&maja);
        assert_eq!(home.run(kid(&maja, "2222", "  ", 6)), Invalid);
        assert_eq!(home.run(kid("nobody", "0000", "Maja", 6)), NameTaken);
        assert_eq!(home.run(kid(&maja, "2222", "Maja", 6)), NameTaken);
    }
}

mod removing {
    use super::*;

    #[test]
    fn the_admin_removes_a_grown_up_and_its_kids_go_with_it() {
        let Household {
            home,
            andre,
            maja,
            mia,
            leo,
        } = household();
        assert_eq!(home.run(remove(&andre, "1111", &maja)), Done);
        let left: Vec<String> = home.rows().into_iter().map(|row| row.id).collect();
        assert!(left.contains(&andre) && left.contains(&leo));
        assert!(!left.contains(&maja) && !left.contains(&mia));
    }

    #[test]
    fn the_admin_cannot_be_removed_by_anyone() {
        let Household {
            home, andre, maja, ..
        } = household();
        assert_eq!(home.run(remove(&andre, "1111", &andre)), NotAllowed);
        assert_eq!(home.run(remove(&maja, "2222", &andre)), NotAllowed);
    }

    #[test]
    fn a_parent_removes_its_own_kid_and_nobody_elses() {
        let Household {
            home,
            andre,
            maja,
            mia,
            leo,
        } = household();
        assert_eq!(home.run(remove(&andre, "1111", &mia)), NotAllowed);
        assert_eq!(home.run(remove(&maja, "2222", &leo)), NotAllowed);
        assert_eq!(home.run(remove(&maja, "2222", &mia)), Done);
        assert!(home.row(&mia).is_none());
    }

    #[test]
    fn a_kid_from_before_parents_is_the_admins() {
        let Household {
            home, andre, maja, ..
        } = household();
        let tv_kids = home.synced("TV kids", true);
        assert_eq!(home.run(remove(&maja, "2222", &tv_kids)), NotAllowed);
        assert_eq!(home.run(remove(&andre, "1111", &tv_kids)), Done);
    }

    #[test]
    fn removing_a_kid_takes_only_that_kid_even_one_another_kid_names_as_parent() {
        let Household { home, andre, .. } = household();
        // Sync sets a parent by name, so a kid can end up naming another kid.
        let parent = ProfileRoles {
            parent: Some("tim".into()),
            ..Default::default()
        };
        home.import(vec![
            viewer("Tim", true, ProfileRoles::default()),
            viewer("Odd", true, parent),
        ]);
        let (tim, odd) = (home.named("Tim"), home.named("Odd"));
        assert_eq!(odd.parent_id.as_deref(), Some(tim.id.as_str()));
        assert_eq!(home.run(remove(&andre, "1111", &tim.id)), Done);
        assert!(home.row(&odd.id).is_some());
    }

    #[test]
    fn somebody_not_there_is_not_found() {
        let Household { home, andre, .. } = household();
        assert_eq!(home.run(remove(&andre, "1111", "nobody")), NotFound);
        assert_eq!(home.run(remove("nobody", "1111", &andre)), NotFound);
    }
}

mod a_kids_limit {
    use super::*;

    #[test]
    fn is_its_parents_to_set_and_nobody_elses() {
        let Household {
            home,
            andre,
            maja,
            mia,
            ..
        } = household();
        assert_eq!(home.run(kids_age(&maja, "2222", &mia, 6)), Done);
        assert_eq!(home.limit(&mia), Some(6));
        assert_eq!(home.run(kids_age(&andre, "1111", &mia, 12)), NotAllowed);
        assert_eq!(home.run(kids_age(&maja, "2222", &mia, 9)), Invalid);
        assert_eq!(home.run(kids_age(&maja, "2222", &maja, 6)), NotAllowed);
    }

    /// The same limit written again is still a write: it is stamped past the
    /// last one, as the web's is.
    #[test]
    fn every_write_moves_the_stamp_even_to_the_same_limit() {
        let Household {
            home, maja, mia, ..
        } = household();
        assert_eq!(home.run(kids_age(&maja, "2222", &mia, 12)), Done);
        assert_eq!(home.column::<i64>("kids_age_updated_at", &mia), T + 1);
    }

    #[test]
    fn a_change_outdates_one_another_devices_clock_stamped_however_far_ahead() {
        let Household {
            home, maja, mia, ..
        } = household();
        let ahead = T + 60_000;
        let limit = KidsAge {
            age: 12,
            updated_at: ahead as f64,
        };
        home.import(vec![viewer(
            "Mia",
            true,
            ProfileRoles {
                kids_age: Some(limit),
                ..Default::default()
            },
        )]);
        assert_eq!(home.run(kids_age(&maja, "2222", &mia, 6)), Done);
        assert_eq!(home.limit(&mia), Some(6));
        assert_eq!(home.column::<i64>("kids_age_updated_at", &mia), ahead + 1);
    }

    #[test]
    fn a_change_over_one_stamped_at_the_last_safe_time_stays_a_time_every_peer_keeps() {
        let Household {
            home, maja, mia, ..
        } = household();
        let limit = KidsAge {
            age: 12,
            updated_at: MAX_STAMP,
        };
        home.import(vec![viewer(
            "Mia",
            true,
            ProfileRoles {
                kids_age: Some(limit),
                ..Default::default()
            },
        )]);
        assert_eq!(home.run(kids_age(&maja, "2222", &mia, 6)), Done);
        assert_eq!(
            home.column::<i64>("kids_age_updated_at", &mia),
            MAX_STAMP as i64
        );
    }
}

mod wrong_pins {
    use super::*;

    #[test]
    fn five_in_a_row_for_one_profile_and_even_its_right_pin_waits() {
        let Household {
            home, andre, leo, ..
        } = household();
        for pin in ["0000", "0001", "0002", "0003", "0004"] {
            assert_eq!(home.run(unlock(&andre, pin)), WrongPin);
        }
        assert_eq!(home.run(unlock(&andre, "1111")), Wait { seconds: 60 });
        // Every call that compares that profile's PIN waits, not only entering.
        assert_eq!(
            home.run(kids_age(&andre, "1111", &leo, 12)),
            Wait { seconds: 60 }
        );
    }

    #[test]
    fn the_seconds_left_follow_the_clock_and_the_wait_ends_on_time() {
        let Household { home, andre, .. } = household();
        home.start_wait(&andre);
        assert_eq!(
            home.at(T + 30_500, unlock(&andre, "1111")),
            Wait { seconds: 30 }
        );
        assert_eq!(
            home.at(T + 59_999, unlock(&andre, "1111")),
            Wait { seconds: 1 }
        );
        assert_eq!(home.at(T + 60_000, unlock(&andre, "1111")), Done);
    }

    #[test]
    fn the_wait_is_that_profiles_and_another_grown_ups_pin_is_still_compared() {
        let Household {
            home, andre, maja, ..
        } = household();
        home.start_wait(&andre);
        assert_eq!(home.run(unlock(&maja, "2222")), Done);
    }

    #[test]
    fn a_right_pin_for_ones_own_profile_does_not_wash_out_the_guesses_at_another() {
        let Household {
            home, andre, maja, ..
        } = household();
        for _ in 0..4 {
            assert_eq!(home.run(unlock(&andre, "0000")), WrongPin);
        }
        assert_eq!(home.run(unlock(&maja, "2222")), Done);
        assert_eq!(home.run(unlock(&andre, "0000")), WrongPin);
        assert_eq!(home.run(unlock(&andre, "1111")), Wait { seconds: 60 });
    }

    #[test]
    fn a_pin_less_grown_up_acting_while_another_profile_waits_is_told_it_has_none() {
        let Household { home, andre, .. } = household();
        let sam = home.synced("Sam", false);
        home.start_wait(&andre);
        assert_eq!(home.run(kid(&sam, "1234", "Zoe", 6)), NoPin);
    }

    #[test]
    fn a_current_pin_that_is_not_four_digits_is_simply_wrong_and_counts() {
        let Household { home, andre, .. } = household();
        for pin in ["12", "abcd", "", "11111", "1111 "] {
            assert_eq!(home.run(unlock(&andre, pin)), WrongPin);
        }
        assert_eq!(home.run(unlock(&andre, "1111")), Wait { seconds: 60 });
    }

    #[test]
    fn bad_input_somebody_not_there_and_what_no_pin_could_allow_are_said_before_the_wait() {
        let Household {
            home,
            andre,
            maja,
            mia,
            ..
        } = household();
        home.start_wait(&maja);
        assert_eq!(home.run(kids_age(&andre, "1111", &mia, 9)), Invalid);
        assert_eq!(home.run(remove("nobody", "1111", &mia)), NotFound);
        assert_eq!(home.run(kid(&mia, "0000", "Zoe", 6)), NotAllowed);
        assert_eq!(home.run(claim(&maja, "2222")), NotAllowed);
        assert_eq!(home.run(first("Zoe", "3333")), NotAllowed);
        // The rule comes after the PIN, so this one waits.
        assert_eq!(
            home.run(remove(&maja, "2222", &andre)),
            Wait { seconds: 60 }
        );
    }

    #[test]
    fn only_a_call_about_to_compare_a_pin_waits() {
        let Household {
            home, maja, mia, ..
        } = household();
        let sam = home.synced("Sam", false);
        home.start_wait(&maja);
        assert_eq!(home.run(unlock(&mia, "")), Done);
        assert_eq!(home.run(unlock(&sam, "1234")), NoPin);
        assert_eq!(home.run(set_pin(&sam, "", &sam, "4444")), Done);
    }

    #[test]
    fn a_right_pin_wipes_the_count() {
        let Household { home, andre, .. } = household();
        for _ in 0..2 {
            for _ in 0..4 {
                assert_eq!(home.run(unlock(&andre, "0000")), WrongPin);
            }
            assert_eq!(home.run(unlock(&andre, "1111")), Done);
        }
    }

    #[test]
    fn a_kid_proves_nothing_so_it_adds_nothing_to_the_count() {
        let Household {
            home, andre, mia, ..
        } = household();
        for _ in 0..5 {
            assert_eq!(home.run(kid(&mia, "0000", "Zoe", 6)), NotAllowed);
        }
        assert_eq!(home.run(unlock(&andre, "1111")), Done);
    }

    #[test]
    fn a_wrong_pin_is_said_before_what_the_rule_would_have_said() {
        let Household {
            home, andre, maja, ..
        } = household();
        assert_eq!(home.run(remove(&maja, "0000", &andre)), WrongPin);
    }

    /// On a device a child holds, swiping the app away is a restart; a wait
    /// that ended with the process would cost a guesser seconds, not a minute.
    #[test]
    fn a_restart_keeps_the_wait_and_it_still_ends_on_time() {
        let Household {
            mut home, andre, ..
        } = household();
        home.start_wait(&andre);
        home.restart();
        assert_eq!(
            home.at(T + 30_500, unlock(&andre, "1111")),
            Wait { seconds: 30 }
        );
        home.restart();
        assert_eq!(home.at(T + 60_000, unlock(&andre, "1111")), Done);
    }

    /// A box that boots before its time is set reads a clock far behind the
    /// one a stored wait began on: the profile waits a minute from then, not
    /// until the clock catches up.
    #[test]
    fn a_clock_put_back_holds_the_profile_a_minute_not_until_it_catches_up() {
        let Household { home, andre, .. } = household();
        home.start_wait(&andre);
        let a_day_back = T - 86_400_000;
        assert_eq!(
            home.at(a_day_back, unlock(&andre, "1111")),
            Wait { seconds: 60 }
        );
        assert_eq!(home.at(a_day_back + 60_000, unlock(&andre, "1111")), Done);
    }

    #[test]
    fn a_restart_keeps_the_wrong_pins_short_of_a_wait() {
        let Household {
            mut home, andre, ..
        } = household();
        for _ in 0..4 {
            assert_eq!(home.run(unlock(&andre, "0000")), WrongPin);
        }
        home.restart();
        assert_eq!(home.run(unlock(&andre, "0000")), WrongPin);
        assert_eq!(home.run(unlock(&andre, "1111")), Wait { seconds: 60 });
    }

    /// One count per player: every management call reaches the same one,
    /// however many calls apart the guesses were.
    #[test]
    fn the_count_is_the_players_not_one_calls() {
        let Household {
            home, andre, maja, ..
        } = household();
        home.start_wait(&andre);
        assert_eq!(
            home.run(remove(&andre, "1111", &maja)),
            Wait { seconds: 60 }
        );
        assert!(home.row(&maja).is_some());
    }
}
