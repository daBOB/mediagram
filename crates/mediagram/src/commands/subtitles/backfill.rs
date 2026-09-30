//! `mediagram subtitles backfill` in sending mode: hands a set's source to
//! `subtitles::attach` and keeps the books. This file holds what folders
//! and the channel share (one set, `--redo`) and the folder run.

use std::path::{Path, PathBuf};

use anyhow::Result;

use super::BackfillArgs;
use super::dry_run::survey;
use super::match_source::{Match, Verdict};
use super::session::{PUBLISH_EVERY, Session, connected};
use crate::channel_index::remote::ChannelRemote;
use crate::config::Config;
use crate::index::{db, subtitles};
use crate::subtitles::{Input, attach};

/// Set when a channel run finds no German or English text track, so later
/// runs do not extract it again; `--redo` clears it.
pub const NONE_KEY: &str = "subs-none:";

#[derive(Debug, PartialEq, Eq)]
pub enum Sent {
    Bundled(Vec<String>),
    /// The source has no German or English text track.
    Nothing,
    Skipped,
}

#[derive(Debug, Default)]
pub struct Tally {
    pub bundled: usize,
    pub nothing: usize,
    pub skipped: usize,
    pub failed: usize,
    /// The run gave up on repeated failures rather than running out of sets.
    pub aborted: bool,
}

impl Tally {
    pub fn add(&mut self, set_id: &str, sent: Result<Sent>) {
        match sent {
            Ok(Sent::Bundled(labels)) => {
                self.bundled += 1;
                println!("{set_id}: {}", labels.join(", "));
            }
            Ok(Sent::Nothing) => {
                self.nothing += 1;
                println!("{set_id}: no German or English text track");
            }
            Ok(Sent::Skipped) => self.skipped += 1,
            Err(err) => {
                self.failed += 1;
                eprintln!("warning: {set_id}: {err:#}");
            }
        }
    }

    pub fn print(&self) {
        println!(
            "bundled {}, nothing to attach {}, skipped {}, failed {}",
            self.bundled, self.nothing, self.skipped, self.failed
        );
    }
}

/// Attaches one set's bundle. A set that has one is left alone, unless
/// `redo`: then the new bundle is recorded first, and the old message is
/// deleted only after the index naming the new one is published (see
/// `Session::replaced`), so a reader never finds a set whose bundle is gone.
pub async fn send_set<R: ChannelRemote>(
    session: &mut Session<'_, R>,
    set_id: &str,
    input: &Input,
    redo: bool,
) -> Result<Sent> {
    let old = subtitles::bundle_message(session.conn, set_id)?;
    if old.is_some() && (!redo || session.done_this_run(set_id)?) {
        return Ok(Sent::Skipped);
    }
    let old_chat = subtitles::bundle_chat_id(session.conn, set_id)?;
    if redo {
        db::delete_meta(session.conn, &format!("{NONE_KEY}{set_id}"))?;
    }
    let attached = attach(session.conn, session.remote, set_id, input).await?;
    // Only a picture track that could not be read: nothing was sent.
    let Some(done) = attached.filter(|done| !done.labels.is_empty()) else {
        return Ok(Sent::Nothing);
    };
    let new = subtitles::bundle_message(session.conn, set_id)?;
    if let Some(old) = old.filter(|old| Some(*old) != new) {
        match i32::try_from(old) {
            Ok(id) if old_chat == Some(session.remote.chat_id()) => session.replaced(id),
            _ => eprintln!(
                "warning: {set_id}: the old bundle message {old} is not in this channel and stays"
            ),
        }
    }
    session.recorded().await;
    Ok(Sent::Bundled(done.labels))
}

pub async fn run_folders(cfg: &Config, args: BackfillArgs) -> Result<()> {
    connected(cfg, !args.no_push, PUBLISH_EVERY, async |session, _| {
        let found = survey(session.conn, &args.folders).await?;
        send_matches(session, &found.matches, &args.accept_fallback, &args.redo).await;
        Ok(())
    })
    .await
}

/// Sends each matched file's subtitles. A name-and-duration match is sent
/// only when its file is named in `accepted`.
pub async fn send_matches<R: ChannelRemote>(
    session: &mut Session<'_, R>,
    matches: &[Match],
    accepted: &[PathBuf],
    redo: &[String],
) -> Tally {
    let accepted: Vec<PathBuf> = accepted.iter().map(|p| canonical(p)).collect();
    let mut tally = Tally::default();
    let (mut unsent_fallbacks, mut redone) = (0, Vec::new());
    for m in matches {
        if session.stopping() {
            break;
        }
        let set_id = match &m.verdict {
            Verdict::Matched(set) => set,
            Verdict::Fallback(set) if accepted.contains(&canonical(&m.path)) => set,
            Verdict::Fallback(_) => {
                unsent_fallbacks += 1;
                continue;
            }
            _ => continue,
        };
        let redoing = redo.contains(set_id);
        let sent = send_set(session, set_id, &Input::File(m.path.clone()), redoing).await;
        let worked = matches!(sent, Ok(Sent::Bundled(_)));
        if redoing {
            redone.push(set_id.clone());
        }
        tally.add(set_id, sent);
        if worked {
            session.pause().await;
        }
    }
    tally.print();
    if unsent_fallbacks > 0 {
        println!(
            "{unsent_fallbacks} name-and-duration match(es) not sent; name each with --accept-fallback <file> after checking it"
        );
    }
    for set in redo.iter().filter(|s| !redone.contains(s)) {
        println!("--redo {set}: no file in these folders matched it");
    }
    tally
}

fn canonical(path: &Path) -> PathBuf {
    path.canonicalize().unwrap_or_else(|_| path.to_path_buf())
}
