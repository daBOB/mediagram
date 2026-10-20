use super::*;
use crate::state::profiles::ProfileOutcome::Done;

const PIN: &str = "1234";

#[tokio::test]
async fn households_view_says_what_this_device_joined_and_everyone() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    core.state_db.with(|conn| crate::state::sync::mark_round_imported(conn, "household")).unwrap();
    assert_eq!(core.clone().create_first_admin("André".into(), PIN.into()).await, Done);
    let andre = core.clone().profiles().await.remove(0).id;
    let maja = NewHouseholdAdmin::New { name: "Maja".into(), new_pin: "5678".into() };
    assert_eq!(core.clone().create_household(andre, PIN.into(), "Flat".into(), maja).await, Done);

    let view = core.clone().households().await;
    let everyone: Vec<&str> = view.everyone.iter().map(|one| one.name.as_str()).collect();
    let names: Vec<&str> = view.households.iter().map(|one| one.name.as_str()).collect();
    let flat = view.households[0].clone();
    assert_eq!(view.joined.as_deref(), Some("home"));
    assert_eq!(everyone, ["André", "Maja"], "everyone, not only this device's household");
    assert_eq!(names, ["Flat", "Home"]);
    assert_eq!(flat.admin_id, view.everyone.iter().find(|one| one.name == "Maja").map(|one| one.id.clone()));
    assert_eq!(core.clone().profiles().await.len(), 1);

    assert_eq!(core.clone().join_household(flat.id.clone(), "5678".into()).await, Done);

    assert_eq!(core.clone().households().await.joined, Some(flat.id));
    let here: Vec<String> = core.profiles().await.into_iter().map(|one| one.name).collect();
    assert_eq!(here, ["Maja"]);
}
