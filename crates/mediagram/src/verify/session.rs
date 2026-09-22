//! Per-set verification: pairs local index rows with what Telegram actually
//! holds and folds both into a [`SetReport`]. All the IO orchestration of
//! `verify` lives here; the decisions themselves stay in [`super::report`].

use std::collections::HashMap;

use anyhow::Result;
use grammers_client::media::Document;
use grammers_client::message::Message;
use mediagram_core::document::message_document;
use rusqlite::Connection;

use super::download_hash::{fetch_messages, hash_document};
use super::report::{self, ExpectedPart, ObservedMessage, PartVerdict, SetReport};
use super::{LocalPart, clear_verified, mark_verified, verified_since};
use crate::index::sets::SetRow;
use crate::index::status::PartStatus;
use crate::telegram::client::Tg;

/// One set queued for verification: its index row plus every part row.
pub struct SetPlan {
    pub set_id: String,
    pub row: SetRow,
    pub parts: Vec<LocalPart>,
}

impl SetPlan {
    /// Bytes `--full` would download for this set, `None` if the recorded
    /// lengths overflow (a corrupt index rather than a real library).
    pub fn total_bytes(&self, since: Option<i64>) -> Option<u64> {
        self.parts
            .iter()
            .filter(|p| !verified_since(p, since))
            .try_fold(0u64, |acc, p| acc.checked_add(p.byte_length))
    }
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
    let local_issue = match plan
        .parts
        .iter()
        .try_fold(0u64, |a, p| a.checked_add(p.byte_length))
    {
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
        .filter(|p| p.status == PartStatus::Done && in_this_chat(p, chat_id))
        .filter_map(|p| p.message_id)
        .filter_map(|id| i32::try_from(id).ok())
        .collect();
    let messages = fetch_messages(&tg.client, tg.channel, &ids, max_attempts).await?;

    let mut parts = Vec::with_capacity(plan.parts.len());
    for part in &plan.parts {
        parts.push(
            verify_part(
                conn,
                tg,
                &plan.set_id,
                part,
                chat_id,
                &messages,
                full,
                since,
            )
            .await?,
        );
    }
    Ok(SetReport {
        set_id: plan.set_id.clone(),
        local_issue,
        parts,
    })
}

/// A part with no recorded `chat_id` predates that column being written and
/// is assumed to live in the configured channel.
fn in_this_chat(part: &LocalPart, chat_id: i64) -> bool {
    part.chat_id.is_none_or(|recorded| recorded == chat_id)
}

#[allow(clippy::too_many_arguments)]
async fn verify_part(
    conn: &Connection,
    tg: &Tg,
    set_id: &str,
    part: &LocalPart,
    chat_id: i64,
    messages: &HashMap<i32, Message>,
    full: bool,
    since: Option<i64>,
) -> Result<PartVerdict> {
    let expected = ExpectedPart {
        idx: part.idx,
        byte_length: part.byte_length,
        doc_id: part.doc_id,
        sha256: part.sha256.clone(),
        verified_at: part.verified_at,
    };
    let (observed, document) = observe(part, chat_id, messages);
    let mut verdict = report::verify_size(&expected, &observed);

    if full && verdict.size_ok && !verified_since(part, since) {
        if let Some(document) = document {
            let now = crate::clock::now_unix();
            match hash_document(&tg.client, &document, part.byte_length).await {
                Ok(computed) => {
                    verdict = report::apply_hash(verdict, &computed, part.sha256.as_deref(), now);
                    if verdict.hash_ok == Some(true) {
                        mark_verified(conn, set_id, part.idx, now)?;
                    }
                }
                Err(err) => {
                    verdict = report::verify_size(
                        &expected,
                        &ObservedMessage::DownloadFailed(format!("{err:#}")),
                    );
                }
            }
        }
    }

    // A part that fails today must not keep advertising an old success: the
    // row is what `push-index` snapshots to the channel for other clients.
    if verdict.failed() && part.verified_at.is_some() {
        clear_verified(conn, set_id, part.idx)?;
        verdict.verified_at = None;
    }
    Ok(verdict)
}

/// What Telegram shows for this part, plus the document itself when there is
/// one to hash, so `--full` never has to look the message up a second time.
fn observe(
    part: &LocalPart,
    chat_id: i64,
    messages: &HashMap<i32, Message>,
) -> (ObservedMessage, Option<Document>) {
    if part.status != PartStatus::Done {
        return (ObservedMessage::NotUploaded, None);
    }
    if let Some(recorded) = part.chat_id
        && recorded != chat_id
    {
        let observed = ObservedMessage::OtherChat {
            recorded,
            current: chat_id,
        };
        return (observed, None);
    }
    let Some(id) = part.message_id.and_then(|id| i32::try_from(id).ok()) else {
        return (ObservedMessage::NotUploaded, None);
    };
    let Some(message) = messages.get(&id) else {
        return (ObservedMessage::MessageMissing, None);
    };
    match message_document(message) {
        Some((doc, doc_id)) => {
            let observed = ObservedMessage::Document {
                doc_id,
                size: doc.size().map(|s| s as u64),
            };
            (observed, Some(doc))
        }
        None => (ObservedMessage::NoDocument, None),
    }
}

