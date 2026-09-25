//! `mediagram remove`: destroy a set, in the channel and in the index.
//!
//! The only command that loses data irrecoverably. The bytes exist nowhere
//! but the channel, Telegram has no undelete, and a set id is not something
//! anyone recognises — so what will be destroyed is described in full, and
//! nothing happens without `--yes`.

use anyhow::{Context, Result, bail};
use rusqlite::Connection;

use crate::config::Config;
use crate::index::{db, parts, sets};
use crate::remove::apply::{apply_removals, delete_messages};
use crate::remove::plan::{Removal, plan_removal};
use crate::telegram::client::Tg;

pub async fn run(cfg: &Config, set_ids: Vec<String>, dry_run: bool, yes: bool) -> Result<()> {
    run_with(cfg, set_ids, dry_run, yes, async |conn, removals| {
        let tg = Tg::connect(cfg).await?;
        let result = apply_removals(conn, removals, async |removal| {
            delete_messages(&tg.client, tg.channel, removal, cfg.max_attempts).await
        })
        .await;
        tg.shutdown().await;
        result
    })
    .await
}

async fn run_with(
    cfg: &Config,
    set_ids: Vec<String>,
    dry_run: bool,
    yes: bool,
    apply: impl AsyncFnOnce(&Connection, &[Removal]) -> Result<usize>,
) -> Result<()> {
    let conn = db::open(&cfg.data_dir()?)?;

    let mut removals = Vec::new();
    for set_id in &set_ids {
        let set = sets::get_set(&conn, set_id)?
            .with_context(|| format!("no set {set_id} in the index"))?;
        let part_rows = parts::all_parts(&conn, set_id)?;
        removals.push(plan_removal(&set, &part_rows));
    }

    println!("This will permanently delete:\n");
    for removal in &removals {
        println!("{}\n", removal.describe());
    }
    let messages: usize = removals.iter().map(|r| r.message_ids.len()).sum();
    let bytes: u64 = removals.iter().map(|r| r.bytes).sum();
    println!(
        "{} set(s), {messages} message(s), {:.2} GB",
        removals.len(),
        bytes as f64 / 1_073_741_824.0
    );

    if dry_run {
        println!("\ndry run; nothing was deleted");
        return Ok(());
    }
    if !yes {
        // Telegram has no undelete and the bytes are nowhere else. A typo in
        // a set id should cost an error message, not a re-upload.
        bail!("refusing to delete without --yes; run with --dry-run first to see what that means");
    }

    let deleted = apply(&conn, &removals).await?;
    println!("\ndeleted {deleted} message(s)");
    println!("the player's cached chunks for these sets are now stale; they age out on their own");
    Ok(())
}

#[cfg(test)]
#[path = "remove_tests.rs"]
mod tests;
