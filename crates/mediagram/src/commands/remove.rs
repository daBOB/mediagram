//! `mediagram remove`: destroy a set, in the channel and in the index.
//!
//! The only command that loses data irrecoverably. The bytes exist nowhere
//! but the channel, Telegram has no undelete, and a set id is not something
//! anyone recognises — so what will be destroyed is described in full, and
//! nothing happens without `--yes`.

use anyhow::{Context, Result, bail};

use crate::config::Config;
use crate::index::{db, parts, sets};
use crate::remove::apply::{delete_messages, delete_rows};
use crate::remove::plan::plan_removal;
use crate::telegram::client::Tg;

pub async fn run(cfg: &Config, set_ids: Vec<String>, dry_run: bool, yes: bool) -> Result<()> {
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

    let tg = Tg::connect(cfg).await?;
    let mut deleted = 0;
    let mut result = Ok(());
    for removal in &removals {
        match delete_messages(&tg.client, tg.channel, removal, cfg.max_attempts).await {
            Ok(count) => {
                deleted += count;
                // Rows only after the messages are gone, so the index never
                // claims to hold what the channel no longer has.
                if let Err(err) = delete_rows(&conn, &removal.set_id) {
                    result = Err(err);
                    break;
                }
                println!("removed {}", removal.set_id);
            }
            Err(err) => {
                result = Err(err);
                break;
            }
        }
    }
    tg.shutdown().await;
    result?;

    println!("\ndeleted {deleted} message(s)");
    println!("the player's cached chunks for these sets are now stale; they age out on their own");
    Ok(())
}
