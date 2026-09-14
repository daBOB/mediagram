//! `mediagram login`: phone → code → optional 2FA password; persists the session.

use anyhow::Result;

use crate::config::Config;

pub async fn run(_cfg: &Config) -> Result<()> {
    anyhow::bail!("not yet implemented")
}
