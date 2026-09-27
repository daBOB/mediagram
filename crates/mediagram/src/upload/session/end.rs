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

/// Publishes when this session completed a set or an earlier one left a
/// publish owed. Not with `no_push`, and not while another upload runs: that
/// one publishes when it ends, and one pin instead of two is what the flood
/// limit on pins asks for. Whatever is not published here is owed, so the
/// next session that may publish does.
async fn publish<L: Link>(session: &mut Session<'_, L>, no_push: bool) -> Result<()> {
    let owed = pins::publish_owed(&session.conn)?.is_some();
    if session.completed == 0 && !owed {
        return Ok(());
    }
    if no_push || lock::is_held(&session.data_dir) {
        if session.completed > 0 {
            pins::owe_publish(&session.conn)?;
        }
        if !no_push {
            println!("index not pushed: another upload is running and pushes it when it ends");
        }
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
    match published {
        Ok(id) => {
            println!("pushed index as message {id}");
            Ok(())
        }
        Err(err) => {
            if session.completed > 0 {
                pins::owe_publish(&session.conn)?;
            }
            Err(err).context("the index was not pushed; run `mediagram push-index`")
        }
    }
}
