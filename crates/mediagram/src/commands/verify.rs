//! `mediagram verify`: metadata check by default, or (`--full`) re-download
//! and hash every part. Exits non-zero if any part fails.

use std::collections::HashMap;
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result, bail};
use grammers_client::media::Media;
use grammers_client::message::Message;
use rusqlite::Connection;

use crate::config::Config;
use crate::index::{db, sets};
use crate::telegram::client::Tg;
use crate::verify::download_hash::{fetch_messages, hash_document};
use crate::verify::report::{self, ExpectedPart, ObservedMessage, PartVerdict, SetReport};
use crate::verify::{self, LocalPart};

pub async fn run(cfg: &Config, set_id: Option<String>, all: bool, full: bool) -> Result<()> {
    if set_id.is_none() && !all {
        bail!("verify needs a set id, or --all to check every set");
    }

    let conn = db::open(&cfg.data_dir()?)?;
    let set_ids = verify::resolve_set_ids(&conn, set_id.as_deref(), all)?;
    if set_ids.is_empty() {
        println!("no sets to verify");
        return Ok(());
    }
    let plans: Vec<(String, Vec<LocalPart>)> = set_ids
        .into_iter()
        .map(|id| verify::load_parts(&conn, &id).map(|parts| (id, parts)))
        .collect::<Result<_>>()?;
    if full {
        announce_full_cost(&plans);
    }

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let result = verify_all(&conn, &tg, &plans, full, cfg.max_attempts).await;
    tg.shutdown().await;
    let reports = result?;

    let mut failed_sets = 0usize;
    for report in &reports {
        for row in report::render_rows(report) {
            println!("{row}");
        }
        println!("{}", report::summary_line(report));
        if report.failed() {
            failed_sets += 1;
        }
    }
    if failed_sets > 0 {
        bail!("verify: {failed_sets} of {} set(s) failed", reports.len());
    }
    Ok(())
}

/// `--full` streams every part's bytes back down; tell the user the cost
/// up front rather than let it appear as a silent, slow hang.
fn announce_full_cost(plans: &[(String, Vec<LocalPart>)]) {
    let total_parts: usize = plans.iter().map(|(_, p)| p.len()).sum();
    let total_bytes: u64 = plans
        .iter()
        .flat_map(|(_, p)| p.iter())
        .map(|p| p.byte_length)
        .sum();
    println!(
        "--full: about to download {total_bytes} bytes across {total_parts} part(s) to verify hashes"
    );
}

async fn verify_all(
    conn: &Connection,
    tg: &Tg,
    plans: &[(String, Vec<LocalPart>)],
    full: bool,
    max_attempts: u32,
) -> Result<Vec<SetReport>> {
    let mut reports = Vec::with_capacity(plans.len());
    for (set_id, parts) in plans {
        reports.push(verify_one_set(conn, tg, set_id, parts, full, max_attempts).await?);
    }
    Ok(reports)
}

async fn verify_one_set(
    conn: &Connection,
    tg: &Tg,
    set_id: &str,
    local_parts: &[LocalPart],
    full: bool,
    max_attempts: u32,
) -> Result<SetReport> {
    let set_row = sets::get_set(conn, set_id)?
        .ok_or_else(|| anyhow::anyhow!("set {set_id} vanished from the index mid-verify"))?;
    let sum_len: u64 = local_parts.iter().map(|p| p.byte_length).sum();
    let local_issue = report::check_local_invariant(
        set_row.part_count,
        local_parts.len(),
        set_row.total,
        sum_len,
    );

    let ids: Vec<i32> = local_parts
        .iter()
        .filter(|p| p.status == "done")
        .filter_map(|p| p.message_id)
        .filter_map(|id| i32::try_from(id).ok())
        .collect();
    let messages = fetch_messages(&tg.client, tg.channel, &ids, max_attempts).await?;

    let mut parts = Vec::with_capacity(local_parts.len());
    for part in local_parts {
        parts.push(verify_one_part(conn, tg, set_id, part, &messages, full).await?);
    }

    Ok(SetReport {
        set_id: set_id.to_string(),
        local_issue,
        parts,
    })
}

async fn verify_one_part(
    conn: &Connection,
    tg: &Tg,
    set_id: &str,
    part: &LocalPart,
    messages: &HashMap<i32, Message>,
    full: bool,
) -> Result<PartVerdict> {
    let expected = ExpectedPart {
        idx: part.idx,
        byte_length: part.byte_length,
        doc_id: part.doc_id,
        sha256: part.sha256.clone(),
        verified_at: part.verified_at,
    };

    let observed_message = if part.status != "done" {
        None
    } else {
        part.message_id.and_then(|id| i32::try_from(id).ok())
    };
    let Some(message_id) = observed_message else {
        return Ok(report::verify_size(
            &expected,
            &ObservedMessage::NotUploaded,
        ));
    };
    let Some(message) = messages.get(&message_id) else {
        return Ok(report::verify_size(
            &expected,
            &ObservedMessage::MessageMissing,
        ));
    };
    let document = match message.media() {
        Some(Media::Document(doc)) => doc,
        _ => return Ok(report::verify_size(&expected, &ObservedMessage::NoDocument)),
    };
    let observed = ObservedMessage::Document {
        doc_id: document.id(),
        size: document.size().map(|s| s as u64),
    };
    let mut verdict = report::verify_size(&expected, &observed);

    if full && verdict.size_ok {
        let computed = hash_document(&tg.client, &document)
            .await
            .with_context(|| format!("downloading part {} of set {set_id} to hash it", part.idx))?;
        let now = now_unix();
        verdict = report::apply_hash(verdict, &computed, part.sha256.as_deref(), now);
        if verdict.hash_ok == Some(true) {
            verify::mark_verified(conn, set_id, part.idx, now)?;
        }
    }
    Ok(verdict)
}

fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}
