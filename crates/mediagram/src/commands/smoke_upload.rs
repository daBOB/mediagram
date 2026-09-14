//! Hidden smoke test: upload one file with a smoke caption, print message id, delete it.

use anyhow::Result;

use crate::config::Config;

pub async fn run(_cfg: &Config, _file: &std::path::Path) -> Result<()> {
    anyhow::bail!("not yet implemented")
}
