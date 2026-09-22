//! Finishing one set that `add` has already planned: the half of an upload
//! that is only bytes.
//!
//! Split out because `add` can now either watch that happen or hand it to a
//! process that outlives the terminal, and both must do exactly the same
//! thing. The upload itself is [`crate::upload::finish::finish_one`], which
//! `resume` also runs for every pending set through one connection.

use std::path::Path;

use anyhow::{Context, Result};

use super::push_index;
use crate::config::Config;
use crate::index::{db, sets};
use crate::telegram::client::Tg;
use crate::upload::finish::finish_one;
use crate::upload::lock;
use crate::upload::transport::TelegramTransport;

/// Uploads what is left of `set_id`, then deletes `delete` if the set
/// reached the channel whole, and pushes the index unless told not to.
pub async fn run(cfg: &Config, set_id: &str, delete: Option<&Path>, no_push: bool) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    // One upload at a time: a file added while another is going up waits for
    // it, the way a show's episodes wait for each other, rather than the two
    // halving each other's bandwidth.
    let _lock = lock::acquire(&data_dir, || {
        println!("waiting for the upload already running");
    })
    .await?;
    let conn = db::open(&data_dir)?;
    let set = sets::get_set(&conn, set_id)?
        .with_context(|| format!("no set {set_id} in the index"))?;

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let transport = TelegramTransport::new(&tg, cfg.max_attempts);
    let result = finish_one(&conn, &transport, cfg.throttle_ms, &set, &data_dir).await;
    tg.shutdown().await;
    let complete = result.context("uploading set")?;

    println!("set {set_id} added");
    if let Some(path) = delete {
        report_deletion(path, complete, set.total);
    }
    if complete && !no_push {
        push_index::run(cfg).await.with_context(|| {
            format!("set {set_id} is complete but the index push failed; run `mediagram push-index`")
        })?;
    }
    Ok(())
}

/// Removes the file the user named, once the index says every part of it is
/// in the channel. The parts are not read back; that is what `verify` is for
/// and what a caller whose local copy is the only other one should run first.
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
