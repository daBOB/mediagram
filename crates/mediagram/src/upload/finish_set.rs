//! Finishing a planned set over a connection the caller holds: the bytes,
//! then the source file if the person asked for it gone.
//!
//! The upload itself is [`crate::upload::finish::finish_one`], which `resume`
//! also runs for every pending set; this adds the one-upload-at-a-time lock
//! and what a person is told. [`Uploader`] is the connection a command that
//! walks many files holds across all of them.

use std::path::Path;

use anyhow::{Context, Result};

use crate::config::Config;
use crate::index::{db, sets};
use crate::telegram::client::Tg;
use crate::upload::finish::finish_one;
use crate::upload::lock;
use crate::upload::transport::TelegramTransport;

/// Uploads what is left of `set_id` over a connection the caller holds, then
/// deletes `delete` if the set reached the channel whole. Returns whether it
/// did. A bulk command holds one connection for every set it walks.
pub async fn finish_with(
    cfg: &Config,
    tg: &Tg,
    set_id: &str,
    delete: Option<&Path>,
) -> Result<bool> {
    let data_dir = cfg.data_dir()?;
    // One upload at a time: a file added while another is going up waits for
    // it, the way a show's episodes wait for each other, rather than the two
    // halving each other's bandwidth.
    let _lock = lock::acquire(&data_dir, || {
        println!("waiting for the upload already running");
    })
    .await?;
    let conn = db::open(&data_dir)?;
    let set =
        sets::get_set(&conn, set_id)?.with_context(|| format!("no set {set_id} in the index"))?;

    let transport = TelegramTransport::new(tg, cfg.max_attempts);
    let complete = finish_one(&conn, &transport, cfg.throttle_ms, &set, &data_dir)
        .await
        .context("uploading set")?;

    println!("set {set_id} added");
    if let Some(path) = delete {
        report_deletion(path, complete, set.total);
    }
    Ok(complete)
}

/// One Telegram connection for a command that uploads many sets in turn,
/// made when the first of them needs it: a re-run that finds everything
/// already uploaded never connects at all.
pub struct Uploader<'a> {
    cfg: &'a Config,
    tg: Option<Tg>,
}

impl<'a> Uploader<'a> {
    pub fn new(cfg: &'a Config) -> Uploader<'a> {
        Uploader { cfg, tg: None }
    }

    /// [`finish_with`] over this uploader's connection.
    pub async fn finish(&mut self, set_id: &str, delete: Option<&Path>) -> Result<bool> {
        let tg = match &mut self.tg {
            Some(tg) => tg,
            slot => slot.insert(
                Tg::connect(self.cfg)
                    .await
                    .context("connecting to Telegram")?,
            ),
        };
        finish_with(self.cfg, tg, set_id, delete).await
    }

    pub async fn close(self) {
        if let Some(tg) = self.tg {
            tg.shutdown().await;
        }
    }
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
