//! `mediagram resume`: finish every set left pending by an interrupted `add`.

use anyhow::{Result, ensure};

use crate::config::Config;
use crate::index::{db, sets};
use crate::upload::session::link::TelegramLink;
use crate::upload::session::{Item, Outcome, Session, Set, Step};

/// Finishes every pending set in one upload session, which publishes once
/// at the end (unless `no_push`). A set a background `add` is finishing right
/// now is waited for and then found complete, not sent twice.
pub async fn run(cfg: &Config, no_push: bool) -> Result<()> {
    let pending = sets::list_pending(&db::open(&cfg.data_dir()?)?)?;
    if pending.is_empty() {
        println!("no pending sets");
        return Ok(());
    }
    let items = pending.into_iter().map(|set| Item {
        tag: set.set_id.clone(),
        set: Set::Planned(set.set_id),
        delete_source: None,
    });
    let mut session = Session::new(cfg, TelegramLink::new(cfg))?;
    let counts = session
        .upload(items, |id, step| match step {
            Step::End(Outcome::AlreadyHeld) => {
                println!("set {id} already finished by another upload");
            }
            Step::End(Outcome::Failed(err) | Outcome::Blocked(err)) => {
                println!("set {id}: {err:#}");
            }
            _ => {}
        })
        .await;
    session.end(no_push).await?;
    ensure!(
        counts.blocked == 0,
        "{} set(s) blocked by unavailable or changed sources",
        counts.blocked
    );
    ensure!(
        counts.failed == 0,
        "{} set(s) could not be resumed",
        counts.failed
    );
    Ok(())
}
