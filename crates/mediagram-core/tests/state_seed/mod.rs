//! A household made the way the surface makes one, for the integration tests
//! that need profiles to exist: the first grown-up runs it, every later one
//! is added by that one, all with the PIN [`PIN`]; every kid belongs to the
//! first grown-up and is limited to FSK 12.

use std::sync::Arc;

use mediagram_core::api::Core;
use mediagram_core::state::profiles::{Profile, ProfileOutcome};

pub const PIN: &str = "1234";

/// Grown-ups, then kids, each answered as `profiles` lists it, in the order
/// given.
pub async fn household(player: &Arc<Core>, grown_ups: &[&str], kids: &[&str]) -> Vec<Profile> {
    let mut made: Vec<Profile> = Vec::new();
    for name in grown_ups.iter().chain(kids) {
        let (name, kid) = (name.to_string(), kids.contains(name));
        let outcome = match made.first() {
            None => player.clone().create_first_admin(name.clone(), PIN.into()).await,
            Some(admin) if kid => {
                let admin = admin.id.clone();
                player.clone().create_kid(admin, PIN.into(), name.clone(), 12).await
            }
            Some(admin) => {
                let admin = admin.id.clone();
                player.clone().create_grown_up(admin, PIN.into(), name.clone(), PIN.into()).await
            }
        };
        assert_eq!(outcome, ProfileOutcome::Done, "{name}");
        let listed = player.clone().profiles().await;
        made.push(listed.into_iter().find(|p| p.name == name).unwrap());
    }
    made
}
