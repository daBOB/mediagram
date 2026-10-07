use super::*;
use crate::state::profiles::Profile;
use crate::state::profiles::ProfileOutcome::{Done, NotAllowed, Wait, WrongPin};

const PIN: &str = "1234";

/// A household whose first grown-up runs it with [`PIN`]. A first profile
/// waits for a sync round, so the player is given one that found nobody.
async fn household(core: &Arc<Core>) -> String {
    core.state_db
        .with(|conn| crate::state::sync::mark_round_imported(conn, "household"))
        .unwrap();
    let made = core
        .clone()
        .create_first_admin("André".into(), PIN.into())
        .await;
    assert_eq!(made, Done);
    named(core, "André").await.id
}

async fn named(core: &Arc<Core>, name: &str) -> Profile {
    let listed = core.clone().profiles().await;
    listed.into_iter().find(|p| p.name == name).unwrap()
}

/// The count is kept per profile across every call that compares its PIN,
/// so guessing through five different screens waits as five tries at one
/// would — even for the right PIN after.
#[tokio::test]
async fn wrong_pins_given_to_different_calls_add_up_to_one_wait() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let admin = household(&core).await;
    let made = core
        .clone()
        .create_kid(admin.clone(), PIN.into(), "Mia".into(), 6)
        .await;
    assert_eq!(made, Done);
    let mia = named(&core, "Mia").await.id;
    let (p, a, wrong) = (|| core.clone(), || admin.clone(), || String::from("0000"));

    let answers = [
        p().create_grown_up(a(), wrong(), "Bea".into(), "1111".into())
            .await,
        p().create_kid(a(), wrong(), "Leo".into(), 12).await,
        p().set_pin(a(), wrong(), a(), "2222".into()).await,
        p().set_kids_age(a(), wrong(), mia.clone(), 12).await,
        p().delete_profile(a(), wrong(), mia).await,
    ];

    assert!(
        answers.iter().all(|answer| *answer == WrongPin),
        "{answers:?}"
    );
    let waited = p().unlock_profile(a(), PIN.into()).await;
    assert!(
        matches!(waited, Wait { seconds } if seconds > 0),
        "{waited:?}"
    );
}

/// A household with grown-ups but no admin — one a sync round brought in —
/// lets one of them claim the role, and a grown-up with no PIN yet takes
/// the claim's as its first.
#[tokio::test]
async fn a_grown_up_claims_the_admin_role_while_nobody_holds_it() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let bea = core.add_profile("Bea", false).id;
    let sam = core.add_profile("Sam", false).id;

    assert_eq!(
        core.clone().claim_admin(bea.clone(), "4321".into()).await,
        Done
    );

    let claimed = named(&core, "Bea").await;
    assert!(claimed.admin && claimed.has_pin);
    assert_eq!(core.clone().unlock_profile(bea, "4321".into()).await, Done);
    assert_eq!(core.claim_admin(sam, "5555".into()).await, NotAllowed);
}

#[tokio::test]
async fn a_grown_up_from_before_pins_sets_its_first_without_one() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let sam = core.add_profile("Sam", false).id;

    let set = core
        .clone()
        .set_pin(sam.clone(), String::new(), sam.clone(), "4444".into())
        .await;

    assert_eq!(set, Done);
    assert_eq!(
        core.clone()
            .unlock_profile(sam.clone(), "4444".into())
            .await,
        Done
    );
    assert_eq!(core.unlock_profile(sam, "0000".into()).await, WrongPin);
}

#[tokio::test]
async fn removing_the_chosen_profile_forgets_the_choice() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let admin = household(&core).await;
    let made = core
        .clone()
        .create_grown_up(admin.clone(), PIN.into(), "Bea".into(), "1111".into())
        .await;
    assert_eq!(made, Done);
    let bea = named(&core, "Bea").await.id;
    assert!(core.clone().choose_profile(bea.clone()).await);

    assert_eq!(
        core.clone().delete_profile(admin, PIN.into(), bea).await,
        Done
    );

    assert_eq!(core.chosen_profile().await, None);
}
