//! One item, start to end, under the upload lock.

use std::path::Path;

use anyhow::{Context, anyhow};

use super::delete_source::report_deletion;
use super::link::Link;
use super::{Item, Outcome, Session, Set, Step, identity};
use crate::index::status::SetStatus;
use crate::index::{lifecycle, sets};
use crate::subtitles::{Input, attach_and_report};
use crate::upload::pipeline::run_set;
use crate::upload::prepare_set::prepare_and_record_set;
use crate::upload::record_document::record_document_set;
use crate::upload::{finish, lock};
use mlib_spec::caption::Kind;

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
                    // The upload that finished it may still be reading the
                    // subtitles from this very file.
                    if matches!(lifecycle::original_of(&self.conn, id), Ok(Some(_))) {
                        println!(
                            "  {} kept: its subtitles are still being read",
                            path.display()
                        );
                        return Some(Outcome::AlreadyHeld);
                    }
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
        // Read before the upload: completing the set forgets it. A set
        // planned before it was recorded falls back to its source, which is
        // the original unless it was remuxed.
        let original = match lifecycle::original_of(&self.conn, set_id) {
            Ok(original) => original.or_else(|| recorded.clone()),
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
        let (transport, remote) = match link.open().await {
            Ok(linked) => linked,
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
            // Before the source is deleted, which is what it reads from. A
            // document has no streams to ask about.
            if let (Some(original), false) = (original, set.kind == Kind::Doc) {
                attach_and_report(conn, remote, set_id, &Input::File(original)).await;
            }
            // Only now may another upload delete the original; a crash
            // before this leaves the file kept, the safe side.
            if let Err(err) = lifecycle::forget_original(conn, set_id) {
                tracing::warn!("original of {set_id} still recorded: {err:#}");
            }
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

#[cfg(test)]
#[path = "item_tests.rs"]
mod tests;
