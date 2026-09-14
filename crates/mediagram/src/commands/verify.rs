//! `mediagram verify`: metadata check, or full re-download + hash with --full.

use anyhow::Result;

use crate::config::Config;

pub async fn run(_cfg: &Config, _set_id: Option<String>, _all: bool, _full: bool) -> Result<()> {
    anyhow::bail!("not yet implemented")
}
