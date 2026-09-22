//! `mediagram verify`: metadata check by default, or (`--full`) re-download
//! and hash every part. Exits non-zero if any part fails.

use anyhow::{Context, Result, bail};

use crate::config::Config;
use crate::index::status::SetStatus;
use crate::index::{db, sets};
use crate::telegram::client::Tg;
use crate::verify::render;
use crate::verify::session::{self, SetPlan};
use crate::verify::{self};

pub async fn run(
    cfg: &Config,
    set_id: Option<String>,
    all: bool,
    full: bool,
    since: Option<i64>,
) -> Result<()> {
    if set_id.is_none() && !all {
        bail!("verify needs a set id, or --all to check every set");
    }

    let conn = db::open(&cfg.data_dir()?)?;
    let set_ids = verify::resolve_set_ids(&conn, set_id.as_deref(), all)?;
    if set_ids.is_empty() {
        println!("no sets to verify");
        return Ok(());
    }

    let mut plans = Vec::with_capacity(set_ids.len());
    for id in set_ids {
        let row = sets::get_set(&conn, &id)?
            .ok_or_else(|| anyhow::anyhow!("set {id} vanished from the index mid-verify"))?;
        // A set still mid-upload is `resume`'s business, not a verification
        // failure; naming one explicitly still checks it strictly.
        if all && row.status != SetStatus::Complete {
            println!("set {id}: {}, skipped", row.status);
            continue;
        }
        let parts = verify::load_parts(&conn, &id)?;
        plans.push(SetPlan {
            set_id: id,
            row,
            parts,
        });
    }
    if plans.is_empty() {
        println!("no complete sets to verify");
        return Ok(());
    }
    if full {
        announce_full_cost(&plans, since);
    }

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let chat_id = tg.chat_id();
    let mut failed = 0usize;
    for plan in &plans {
        match session::verify_set(&conn, &tg, chat_id, plan, full, since, cfg.max_attempts).await {
            Ok(report) => {
                for row in render::render_rows(&report) {
                    println!("{row}");
                }
                println!("{}", render::summary_line(&report));
                if report.failed() {
                    failed += 1;
                }
            }
            // Reported and counted rather than propagated: a set that cannot
            // be reached must not discard the sets already printed.
            Err(err) => {
                println!("set {}: verification aborted: {err:#}", plan.set_id);
                failed += 1;
            }
        }
    }
    tg.shutdown().await;

    if failed > 0 {
        bail!("verify: {failed} of {} set(s) failed", plans.len());
    }
    Ok(())
}

/// `--full` streams every part's bytes back down; tell the user the cost
/// up front rather than let it appear as a silent, slow hang.
fn announce_full_cost(plans: &[SetPlan], since: Option<i64>) {
    let parts: usize = plans
        .iter()
        .map(|p| {
            p.parts
                .iter()
                .filter(|p| !verify::verified_since(p, since))
                .count()
        })
        .sum();
    let bytes: Option<u64> = plans
        .iter()
        .try_fold(0u64, |acc, p| acc.checked_add(p.total_bytes(since)?));
    match bytes {
        Some(bytes) => {
            // Downloads are one 512 KiB request at a time, so this is slow;
            // 10 MB/s is a rough order-of-magnitude figure, not a promise.
            let minutes = bytes / (10 * 1024 * 1024) / 60;
            println!(
                "--full: about to download {bytes} bytes across {parts} part(s) to verify hashes (roughly {minutes} min at 10 MB/s)"
            );
        }
        None => println!("--full: about to download {parts} part(s) to verify hashes"),
    }
}
