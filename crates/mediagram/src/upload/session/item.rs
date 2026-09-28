//! One item, start to end, under the upload lock.

use std::path::Path;

use anyhow::{Context, anyhow};

use super::link::Link;
use super::{Item, Outcome, Session, Set, Step, identity};
use crate::index::status::SetStatus;
use crate::index::{lifecycle, sets};
use crate::upload::pipeline::run_set;
use crate::upload::prepare_set::prepare_and_record_set;
use crate::upload::record_document::record_document_set;
use crate::upload::{finish, lock};

impl<L: Link> Session<'_, L> {
    /// `None` when the session stopped before this item began: it was not
    /// reached, and is not reported.
    pub(super) async fn run<T>(
        &mut self,
        item: &Item<T>,
        say: &mut impl FnMut(&T, Step<'_>),
    ) -> Option<Outcome> {
        // `upload_slots` uploads at a time across processes, taken per item so a file
        // added meanwhile goes between two of this walk's rather than after
        // all of them; what the index says is read only once it is held.
        let waiting = || println!("waiting for the upload already running");
        let _lock = match lock::acquire_slot(&self.data_dir, self.cfg.upload_slots, waiting).await {
            Ok(lock) => lock,
            Err(err) => return self.stop(err, None),
        };
        let status = match identity::status(&self.conn, &item.set) {
            Ok(status) => status,
            Err(err) => return self.stop(err, None),
        };
        let set_id = match (&item.set, status) {
            // The set `add` planned for exactly this file, finished by
            // another upload first: the file can go as surely as if this
            // one had finished it.
            (Set::Planned(id), Some(SetStatus::Complete)) => {
                if let Some(path) = &item.delete_source {
                    let total = sets::get_set(&self.conn, id)
                        .ok()
                        .flatten()
                        .map_or(0, |s| s.total);
                    report_deletion(path, true, total);
                }
                return Some(Outcome::AlreadyHeld);
            }
            (_, Some(SetStatus::Complete)) => return Some(Outcome::AlreadyHeld),
            (Set::Planned(id), Some(SetStatus::Pending)) => {
                say(&item.tag, Step::Start);
                id.clone()
            }
            (Set::Planned(id), None) => {
                return Some(Outcome::Failed(anyhow!("no set {id} in the index")));
            }
            // Started by another run and never finished: `resume`'s to finish.
            (_, Some(SetStatus::Pending)) => return Some(Outcome::Pending),
            (Set::File(new), None) => {
                say(&item.tag, Step::Start);
                match prepare_and_record_set(self.cfg, new).await {
                    Ok(planned) => planned.set_id,
                    Err(err) => return Some(Outcome::Failed(err)),
                }
            }
            (Set::Document(doc), None) => {
                say(&item.tag, Step::Start);
                match record_document_set(self.cfg, doc) {
                    Ok(set_id) => set_id,
                    Err(err) => return Some(Outcome::Failed(err)),
                }
            }
        };
        self.finish(&set_id, item.delete_source.as_deref()).await
    }

    /// Checks the source, connects if nothing has yet, and sends what is
    /// left of the set.
    async fn finish(&mut self, set_id: &str, delete: Option<&Path>) -> Option<Outcome> {
        let planned = Some(Outcome::Pending);
        let set = match sets::get_set(&self.conn, set_id) {
            Ok(Some(set)) => set,
            Ok(None) => return Some(Outcome::Failed(anyhow!("no set {set_id} in the index"))),
            Err(err) => return self.stop(err, planned),
        };
        let recorded = match lifecycle::source_of(&self.conn, set_id) {
            Ok(recorded) => recorded,
            Err(err) => return self.stop(err, planned),
        };
        // Checked before connecting: a run whose every source is gone
        // never connects at all.
        let source = match finish::available_source(&set, recorded.as_deref()).await {
            Ok(source) => source,
            Err(err) => return Some(Outcome::Blocked(err)),
        };
        let Session {
            cfg,
            data_dir,
            conn,
            link,
            stopped,
            ..
        } = self;
        let transport = match link.open().await {
            Ok((transport, _)) => transport,
            Err(err) => {
                *stopped = Some(err);
                return planned;
            }
        };
        let finished = run_set(
            conn,
            transport,
            cfg.throttle_ms,
            &set,
            &source,
            Some(data_dir),
        )
        .await
        .with_context(|| format!("uploading set {set_id}"));
        let complete = match finished {
            Ok(complete) => complete,
            // The transport has already retried each part; what failed now
            // is the connection or the index, and every later item would
            // meet it too. Completing a set is the last step that can fail,
            // and one transaction, so this set is still pending.
            Err(err) => {
                *stopped = Some(err);
                return planned;
            }
        };
        // Completing the set also recorded the publish it is owed, in the
        // same transaction: a walk interrupted after this still leaves it
        // owed, and the next session pays it.
        if complete {
            println!("set {set_id} added");
        }
        if let Some(path) = delete {
            report_deletion(path, complete, set.total);
        }
        Some(if complete {
            Outcome::Uploaded
        } else {
            Outcome::Pending
        })
    }

    fn stop(&mut self, err: anyhow::Error, outcome: Option<Outcome>) -> Option<Outcome> {
        self.stopped = Some(err);
        outcome
    }
}

/// Removes the file the person named, once the index says every part of it
/// is in the channel. The parts are not read back; that is what `verify` is
/// for, and what someone whose local copy is the only other one runs first.
fn report_deletion(path: &Path, complete: bool, total: u64) {
    let name = path
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| path.display().to_string());
    if !complete {
        println!("  {name} kept: not every part reached the channel");
        return;
    }
    match std::fs::remove_file(path) {
        Ok(()) => println!("  {name} deleted, {:.2} GB freed", total as f64 / 1e9),
        Err(err) => println!("  {name} kept: {err}"),
    }
}
