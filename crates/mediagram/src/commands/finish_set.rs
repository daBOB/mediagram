//! `mediagram finish-set`: finishing one set that `add` has already planned,
//! the half of an upload that is only bytes.
//!
//! A command of its own because `add` can either watch that happen or hand it
//! to a process that outlives the terminal, and both must do exactly the same
//! thing: a one-item upload session.

use std::path::Path;

use anyhow::{Result, ensure};

use crate::config::Config;
use crate::upload::session::link::TelegramLink;
use crate::upload::session::{Item, Outcome, Session, Set, Step};

/// Uploads what is left of `set_id`, then deletes `delete` if the set
/// reached the channel whole, and publishes unless told not to.
pub async fn run(cfg: &Config, set_id: &str, delete: Option<&Path>, no_push: bool) -> Result<()> {
    let item = Item {
        tag: set_id,
        set: Set::Planned(set_id.to_string()),
        delete_source: delete.map(Path::to_path_buf),
    };
    let mut session = Session::new(cfg, TelegramLink::new(cfg))?;
    let counts = session
        .upload([item], |id, step| {
            if let Step::End(Outcome::Failed(err) | Outcome::Blocked(err)) = step {
                println!("set {id}: {err:#}");
            }
        })
        .await;
    session.end(no_push).await?;
    ensure!(
        counts.failed + counts.blocked == 0,
        "set {set_id} was not uploaded"
    );
    Ok(())
}
