//! An upload session (see `CONTEXT.md`): one command's run of uploads — the
//! sets it walks, one connection, and at most one publish at the end.
//!
//! Every command that uploads (`add-show`, `add-course`, `add-docu`,
//! `finish-set` behind `add`, `resume`) walks its items through here, so the
//! order each item goes through is written once: take the upload lock, re-read
//! what the local index holds for it, plan it if it is new, check its source,
//! connect if nothing has yet, send, delete its source if asked and it
//! completed, let the lock go. Commands only say what to upload and how each
//! item is called; what they print about each item is theirs.
//!
//! The connection is a [`link::Link`]; Telegram only in `link`.

mod end;
mod identity;
mod item;
pub mod link;

use std::path::PathBuf;

use anyhow::Result;
use rusqlite::Connection;

use crate::config::Config;
use crate::index::db;
use crate::upload::new_set::NewSet;
use crate::upload::record_document::Document;
use link::Link;

/// One thing to upload, and the caller's handle on it (`tag`), handed back
/// with every [`Step`].
pub struct Item<T> {
    pub tag: T,
    pub set: Set,
    /// Removed once this item's set is complete — never on finding it held.
    pub delete_source: Option<PathBuf>,
}

/// What the item is. A new video or document says who it is through its
/// planning data (an episode's show and number, a lesson's or document's
/// collection and number), and is looked up by exactly that before it is
/// planned; one with no such identity is always planned anew.
pub enum Set {
    File(NewSet),
    Document(Document),
    /// Already in the local index: what `add` planned, what `resume` finds.
    Planned(String),
}

/// How one item ended.
#[derive(Debug)]
pub enum Outcome {
    /// Its set is complete, sent by this session.
    Uploaded,
    /// Complete before this session reached it.
    AlreadyHeld,
    /// Planned but not complete: started elsewhere (`resume` owns it), or
    /// left with parts to send.
    Pending,
    /// Its source is missing or changed since it was planned.
    Blocked(anyhow::Error),
    /// Could not be planned, or names no set.
    Failed(anyhow::Error),
}

/// Where one item is: `Start` once it has work to do, `End` exactly once.
pub enum Step<'a> {
    Start,
    End(&'a Outcome),
}

/// How the items of one [`Session::upload`] call ended.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Counts {
    pub uploaded: u32,
    pub held: u32,
    pub pending: u32,
    pub blocked: u32,
    pub failed: u32,
}

impl Counts {
    fn record(&mut self, outcome: &Outcome) {
        match outcome {
            Outcome::Uploaded => self.uploaded += 1,
            Outcome::AlreadyHeld => self.held += 1,
            Outcome::Pending => self.pending += 1,
            Outcome::Blocked(_) => self.blocked += 1,
            Outcome::Failed(_) => self.failed += 1,
        }
    }
}

pub struct Session<'c, L> {
    cfg: &'c Config,
    data_dir: PathBuf,
    conn: Connection,
    link: L,
    completed: usize,
    stopped: Option<anyhow::Error>,
    unreached: usize,
}

impl<'c, L: Link> Session<'c, L> {
    /// Opens the local index; connects to nothing.
    pub fn new(cfg: &'c Config, link: L) -> Result<Self> {
        let data_dir = cfg.data_dir()?;
        let conn = db::open(&data_dir)?;
        Ok(Session {
            cfg,
            data_dir,
            conn,
            link,
            completed: 0,
            stopped: None,
            unreached: 0,
        })
    }

    /// Walks `items` in order, each fully before the next, and says how each
    /// went. Never fails: an item's own trouble is its [`Outcome`]; trouble
    /// with the connection or the local index stops the session, and every
    /// item after that — in this call and any later one — is not reached and
    /// not reported. [`Session::end`] says why.
    pub async fn upload<T>(
        &mut self,
        items: impl IntoIterator<Item = Item<T>>,
        mut say: impl FnMut(&T, Step<'_>),
    ) -> Counts {
        let mut counts = Counts::default();
        for item in items {
            if self.stopped.is_some() {
                self.unreached += 1;
                continue;
            }
            let Some(outcome) = self.run(&item, &mut say).await else {
                self.unreached += 1;
                continue;
            };
            if matches!(outcome, Outcome::Uploaded) {
                self.completed += 1;
            }
            counts.record(&outcome);
            say(&item.tag, Step::End(&outcome));
        }
        counts
    }

    /// Publishes at most once — when this session completed a set or an
    /// earlier one left a publish owed, unless `no_push` or another upload is
    /// running and will publish when it ends — then disconnects. Fails with
    /// what stopped the session, else with a failed publish.
    pub async fn end(self, no_push: bool) -> Result<()> {
        end::end(self, no_push).await
    }
}
