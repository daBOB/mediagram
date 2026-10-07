//! The end of a session: at most one publish, then the verdict.

use anyhow::{Context, Result};

use super::Session;
use super::link::Link;
use crate::channel_index::{ChannelIndex, Mode};
use crate::index::pins;
use crate::upload::lock;

pub(super) async fn end<L: Link>(mut session: Session<'_, L>, no_push: bool) -> Result<()> {
    let published = publish(&mut session, no_push).await;
    let Session {
        link,
        stopped,
        unreached,
        ..
    } = session;
    link.close().await;
    let Some(stop) = stopped else {
        return published;
    };
    let mut said = String::from("the upload stopped");
    if unreached > 0 {
        said.push_str(&format!("; {unreached} item(s) not reached"));
    }
    if let Err(publish) = published {
        said.push_str(&format!(", and {publish:#}"));
    }
    Err(stop.context(said))
}

/// Publishes when a publish is owed — this session completed a set, or an
/// earlier one did and never published. Not with `no_push`, and not while
/// another upload runs: that one publishes when it ends, and one pin instead
/// of two is what the flood limit on pins asks for. Nothing owed is ever
/// lost: the debt stays in the local index until a publish settles it.
async fn publish<L: Link>(session: &mut Session<'_, L>, no_push: bool) -> Result<()> {
    if no_push || pins::publish_owed(&session.conn)?.is_none() {
        return Ok(());
    }
    if lock::any_held(&session.data_dir, session.cfg.upload_slots) {
        println!(
            "index not pushed yet: another upload is running; the next upload or \
             `mediagram push-index` publishes it"
        );
        return Ok(());
    }
    let published = match session.link.open().await {
        Ok((_, remote)) => {
            ChannelIndex::new(remote, &session.data_dir)
                .publish(Mode::AfterPull)
                .await
        }
        Err(err) => Err(err),
    };
    let id = published.context("the index was not pushed; run `mediagram push-index`")?;
    println!("pushed index as message {id}");
    Ok(())
}

#[cfg(test)]
#[path = "end_tests.rs"]
mod tests;
