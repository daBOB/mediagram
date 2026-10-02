//! `mediagram publish-app <apk>`: the newest Android app release, sent to the
//! library channel and pinned, for every installed release build to pick up.

use std::path::Path;

use anyhow::{Context, Result};

use crate::app_release::{badging, publish::publish};
use crate::channel_index::TelegramRemote;
use crate::clock::now_unix;
use crate::config::Config;
use crate::telegram::client::Tg;
use crate::upload::lock;

/// Two releases from this machine at once would race for the pin.
const LOCK_FILE: &str = "publish-app.lock";

pub async fn run(cfg: &Config, apk: &Path) -> Result<()> {
    let badging = badging::read(apk)?;
    let bytes = std::fs::read(apk).with_context(|| format!("reading {}", apk.display()))?;
    let _lock = lock::acquire_file(&cfg.data_dir()?.join(LOCK_FILE), || {
        println!("waiting for another publish-app on this machine to finish");
    })
    .await?;

    let tg = Tg::connect(cfg).await?;
    let remote = TelegramRemote::new(&tg, cfg.max_attempts);
    let result = publish(&remote, &bytes, &badging, now_unix()).await;
    tg.shutdown().await;
    let id = result?;
    println!(
        "published {} (versionCode {}, {} bytes) as message {id}, pinned",
        badging.version_name, badging.version_code, bytes.len()
    );
    Ok(())
}
