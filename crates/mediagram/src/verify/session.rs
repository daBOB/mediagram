//! Per-set verification: pairs local index rows with what Telegram actually
//! holds and folds both into a [`SetReport`]. All the IO orchestration of
//! `verify` lives here; the decisions themselves stay in [`super::report`].

use std::collections::HashMap;

use anyhow::Result;
use rusqlite::Connection;

use super::download_hash::hash_chunks;
use super::report::{self, ExpectedPart, ObservedMessage, PartVerdict, SetReport};
use super::source::{RemoteMessage, TelegramSource, VerificationSource, observe};
use super::{forget_stale_success, mark_verified, other_chat, verified_since};
use crate::index::parts::PartRow;
use crate::index::set_row::SetRow;
use crate::index::status::PartStatus;
use crate::telegram::client::Tg;

/// One set queued for verification: its index row plus every part row.
pub struct SetPlan {
    pub set_id: String,
    pub row: SetRow,
    pub parts: Vec<PartRow>,
}

impl SetPlan {
    /// Bytes `--full` would download for this set, `None` if the recorded
    /// lengths overflow (a corrupt index rather than a real library).
    pub fn total_bytes(&self, since: Option<i64>) -> Option<u64> {
        summed(self.parts.iter().filter(|p| !verified_since(p, since)))
    }
}

/// The parts' lengths added up, `None` if they overflow — which a real
/// library cannot, so it means a corrupt index.
fn summed<'a>(parts: impl Iterator<Item = &'a PartRow>) -> Option<u64> {
    parts
        .map(|p| p.byte_length)
        .try_fold(0u64, u64::checked_add)
}

/// Verifies one set. Returns an error only when the set cannot be checked at
/// all (its index row or message fetch failed); every per-part problem is a
/// [`PartVerdict`] so one bad part never discards the rest of the run.
pub async fn verify_set(
    conn: &Connection,
    tg: &Tg,
    chat_id: i64,
    plan: &SetPlan,
    full: bool,
    since: Option<i64>,
    max_attempts: u32,
) -> Result<SetReport> {
    verify_with(
        conn,
        &TelegramSource { tg, max_attempts },
        chat_id,
        plan,
        full,
        since,
    )
    .await
}

async fn verify_with<S: VerificationSource>(
    conn: &Connection,
    source: &S,
    chat_id: i64,
    plan: &SetPlan,
    full: bool,
    since: Option<i64>,
) -> Result<SetReport> {
    let local_issue = match summed(plan.parts.iter()) {
        Some(sum_len) => report::check_local_invariant(
            plan.row.part_count,
            plan.parts.len(),
            plan.row.total,
            sum_len,
        ),
        None => Some("part lengths sum beyond u64; index is corrupt".to_string()),
    };

    let ids: Vec<i32> = plan
        .parts
        .iter()
        .filter(|p| p.status == PartStatus::Done && other_chat(p, chat_id).is_none())
        .filter_map(|p| p.message_id)
        .filter_map(|id| i32::try_from(id).ok())
        .collect();
    let messages = source.messages(&ids).await?;

    let check = PartCheck {
        conn,
        source,
        set_id: &plan.set_id,
        chat_id,
        messages: &messages,
        full,
        since,
    };
    let mut parts = Vec::with_capacity(plan.parts.len());
    for part in &plan.parts {
        parts.push(check.verify_part(part).await?);
    }
    Ok(SetReport {
        set_id: plan.set_id.clone(),
        local_issue,
        parts,
    })
}

/// What stays the same for every part of one set's verification.
struct PartCheck<'a, S: VerificationSource> {
    conn: &'a Connection,
    source: &'a S,
    set_id: &'a str,
    chat_id: i64,
    messages: &'a HashMap<i32, RemoteMessage<S::Document>>,
    full: bool,
    since: Option<i64>,
}

impl<S: VerificationSource> PartCheck<'_, S> {
    async fn verify_part(&self, part: &PartRow) -> Result<PartVerdict> {
        let Self {
            conn,
            source,
            set_id,
            chat_id,
            messages,
            full,
            since,
        } = *self;
        let expected = ExpectedPart {
            idx: part.idx,
            byte_length: part.byte_length,
            doc_id: part.doc_id,
            sha256: part.sha256.clone(),
            verified_at: part.verified_at,
        };
        let (observed, document) = observe(part, chat_id, messages);
        let mut verdict = report::verify_size(&expected, &observed);

        if full
            && verdict.size_ok
            && !verified_since(part, since)
            && let Some(document) = document
        {
            let now = crate::clock::now_unix();
            match hash_chunks(source.download(document), part.byte_length).await {
                Ok(computed) => {
                    verdict = report::apply_hash(verdict, &computed, part.sha256.as_deref(), now);
                    if verdict.hash_ok == Some(true) {
                        mark_verified(conn, set_id, part.idx, now)?;
                    }
                }
                Err(err) => {
                    let failed = ObservedMessage::DownloadFailed(format!("{err:#}"));
                    verdict = report::verify_size(&expected, &failed);
                }
            }
        }

        forget_stale_success(conn, set_id, part, &mut verdict)?;
        Ok(verdict)
    }
}

#[cfg(test)]
#[path = "session_tests.rs"]
mod tests;
