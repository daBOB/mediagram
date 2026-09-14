//! `mediagram whoami`: prints the signed-in user and the resolved channel.

use anyhow::Result;

use crate::config::Config;

pub async fn run(_cfg: &Config) -> Result<()> {
    anyhow::bail!("not yet implemented")
}
