//! A household made the way the surface makes one, for the integration tests
//! that need profiles to exist: the first grown-up runs it, every later one
//! is added by that one, all with the PIN [`PIN`]; every kid belongs to the
//! first grown-up and is limited to FSK 12. A first profile waits for a sync
//! round, so the player is first given one that found nobody.

use std::path::Path;
use std::sync::Arc;

use mediagram_core::api::Core;
use mediagram_core::state::exchange::import_merged;
use mediagram_core::state::merge::MergedState;
use mediagram_core::state::profiles::{Profile, ProfileOutcome};

pub const PIN: &str = "1234";

/// Grown-ups, then kids, each answered as `profiles` lists it, in the order
/// given. `dir` is the player's data directory.
pub async fn household(player: &Arc<Core>, dir: &Path, grown_ups: &[&str], kids: &[&str]) -> Vec<Profile> {
    first_round_found_nobody(player, dir).await;
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

/// The player in `dir` has taken in one sync round, which found nobody — a
/// new household's first: the import a real round commits, then the mark it
/// leaves (`state/sync/first_round.rs`), on a second connection to the
/// player's own file, which `profiles` opens and migrates first. A round
/// needs Telegram, which no test here reaches. No library has been installed,
/// so the round counts for whichever one the player follows.
pub async fn first_round_found_nobody(player: &Arc<Core>, dir: &Path) {
    player.clone().profiles().await;
    let conn = rusqlite::Connection::open(dir.join("state.db")).unwrap();
    import_merged(&conn, &MergedState::default()).unwrap();
    conn.execute(
        "INSERT OR REPLACE INTO state_meta(key, value) VALUES ('first_round_imported', 'household')",
        [],
    )
    .unwrap();
}
