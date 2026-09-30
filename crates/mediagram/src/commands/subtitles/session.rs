//! What every subtitle run that sends has in common: the upload lock, one
//! Telegram connection, a way to stop between records, a pause between
//! sends, and publishing what was recorded.

use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

use anyhow::{Result, bail};
use rusqlite::Connection;

use crate::channel_index::remote::ChannelRemote;
use crate::channel_index::{ChannelIndex, Mode, TelegramRemote};
use crate::config::Config;
use crate::index::{db, subtitles};
use crate::telegram::client::Tg;
use crate::upload::lock;

/// Between two sends, so a night of them stays well clear of flood limits.
const PACE: Duration = Duration::from_secs(2);
/// A long backfill publishes this often, so a crash loses at most this many
/// recorded sets' worth of publish.
pub const PUBLISH_EVERY: usize = 100;

pub struct Session<'a, R: ChannelRemote> {
    pub conn: &'a Connection,
    pub remote: &'a R,
    data_dir: PathBuf,
    /// A bundle recorded at or after this belongs to this run.
    pub started: i64,
    pace: Duration,
    push: bool,
    /// Publish after every this many recorded sets; `usize::MAX` for once, at the end.
    publish_every: usize,
    stop: Arc<AtomicBool>,
    recorded: usize,
    /// Replaced bundle messages, deleted once the index naming their
    /// replacements is published: until then readers of the published index
    /// still point at them.
    replaced: Vec<i32>,
}

impl<'a, R: ChannelRemote> Session<'a, R> {
    pub fn new(
        conn: &'a Connection,
        remote: &'a R,
        data_dir: PathBuf,
        push: bool,
        publish_every: usize,
        pace: Duration,
        stop: Arc<AtomicBool>,
    ) -> Self {
        Session {
            conn,
            remote,
            data_dir,
            started: crate::clock::now_unix(),
            pace,
            push,
            publish_every,
            stop,
            recorded: 0,
            replaced: Vec::new(),
        }
    }

    /// Ctrl-C was pressed: finish the record in hand, start no other.
    pub fn stopping(&self) -> bool {
        self.stop.load(Ordering::Relaxed)
    }

    /// Whether the set's bundle was recorded by this run already.
    pub fn done_this_run(&self, set_id: &str) -> Result<bool> {
        Ok(subtitles::bundle_uploaded_at(self.conn, set_id)?.is_some_and(|at| at >= self.started))
    }

    /// Counts a recorded set and publishes at every hundredth. A failed
    /// publish here only warns: the index still owes it, and the end of the
    /// run tries again.
    pub async fn recorded(&mut self) {
        self.recorded += 1;
        if self.recorded.is_multiple_of(self.publish_every)
            && let Err(err) = self.publish().await
        {
            eprintln!("warning: publishing the index failed, will try again: {err:#}");
        }
    }

    pub async fn pause(&self) {
        if !self.pace.is_zero() {
            tokio::time::sleep(self.pace).await;
        }
    }

    /// Queues a replaced bundle message for deletion after the next publish.
    pub fn replaced(&mut self, message_id: i32) {
        self.replaced.push(message_id);
    }

    /// The final publish, when anything was recorded and `--no-push` was not
    /// given; then the replaced messages go. Without a publish they stay,
    /// and are listed.
    pub async fn finish(&mut self) -> Result<()> {
        if self.recorded > 0 {
            self.publish().await?;
        }
        if !self.replaced.is_empty() {
            eprintln!(
                "warning: replaced bundle message(s) {:?} stay in the channel: the index naming their replacements is not published; delete them once it is",
                self.replaced
            );
        }
        Ok(())
    }

    async fn publish(&mut self) -> Result<()> {
        if !self.push {
            return Ok(());
        }
        let id = ChannelIndex::new(self.remote, &self.data_dir)
            .publish(Mode::AfterPull)
            .await?;
        println!("published the index as message {id}");
        for old in std::mem::take(&mut self.replaced) {
            if let Err(err) = self.remote.delete_message(old).await {
                eprintln!("warning: the replaced bundle message {old} stays in the channel: {err:#}");
            }
        }
        Ok(())
    }
}

/// The lock covers slot 0 only, so with more slots another upload could run
/// beside this one.
pub fn ensure_single_slot(slots: usize) -> Result<()> {
    if slots > 1 {
        bail!(
            "upload_slots is {slots}: this command holds one slot and would run beside an upload; set it to 1 while subtitles are sent"
        );
    }
    Ok(())
}

fn ctrl_c_flag() -> Arc<AtomicBool> {
    let flag = Arc::new(AtomicBool::new(false));
    let set = Arc::clone(&flag);
    tokio::spawn(async move {
        if tokio::signal::ctrl_c().await.is_ok() {
            set.store(true, Ordering::Relaxed);
            eprintln!("\nstopping after the set in hand; Ctrl-C again quits at once");
        }
        // A record is one transaction, so quitting between two statements of
        // the run loses nothing but the set in hand.
        if tokio::signal::ctrl_c().await.is_ok() {
            std::process::exit(130);
        }
    });
    flag
}

/// Holds the upload lock, connects, runs `body`, publishes what it recorded
/// (even when it failed part-way, so nothing recorded goes unpublished) and
/// disconnects.
pub async fn connected(
    cfg: &Config,
    push: bool,
    publish_every: usize,
    body: impl AsyncFnOnce(&mut Session<'_, TelegramRemote>, &Tg) -> Result<()>,
) -> Result<()> {
    ensure_single_slot(cfg.upload_slots)?;
    let data_dir = cfg.data_dir()?;
    let _lock = lock::acquire(&data_dir, || {
        eprintln!("an upload holds the lock; waiting for it to finish");
    })
    .await?;
    let conn = db::open(&data_dir)?;
    let tg = Tg::connect(cfg).await?;
    let remote = TelegramRemote::new(&tg, cfg.max_attempts);
    let mut session = Session::new(
        &conn,
        &remote,
        data_dir,
        push,
        publish_every,
        PACE,
        ctrl_c_flag(),
    );
    let ran = body(&mut session, &tg).await;
    let published = session.finish().await;
    tg.shutdown().await;
    match (ran, published) {
        (Err(ran), Err(published)) => {
            eprintln!("warning: publishing the index also failed: {published:#}");
            Err(ran)
        }
        (ran, published) => ran.and(published),
    }
}
