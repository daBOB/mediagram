//! `mediagram add`: inspect → resolve → remux → plan → upload parts → index.

use anyhow::Result;

use crate::config::Config;

pub async fn run(_cfg: &Config, _args: super::args::AddArgs) -> Result<()> {
    anyhow::bail!("not yet implemented")
}
