//! `MEDIAGRAM_*` environment overrides applied over the file.

use std::path::PathBuf;

use anyhow::{Context, Result};

use super::Config;

pub(super) fn apply_env(cfg: &mut Config) -> Result<()> {
    let env = |k: &str| {
        std::env::var(format!("MEDIAGRAM_{k}"))
            .ok()
            .filter(|v| !v.is_empty())
    };
    if let Some(v) = env("API_ID") {
        cfg.api_id = v.parse().context("MEDIAGRAM_API_ID")?;
    }
    if let Some(v) = env("API_HASH") {
        cfg.api_hash = v;
    }
    if let Some(v) = env("CHANNEL") {
        cfg.channel = v;
    }
    if let Some(v) = env("TMDB_KEY") {
        cfg.tmdb_key = Some(v);
    }
    if let Some(v) = env("TMDB_LANGUAGE") {
        cfg.tmdb_language = v;
    }
    if let Some(v) = env("PACKAGE_KEY") {
        cfg.package_key = Some(v);
    }
    if let Some(v) = env("PART_SIZE") {
        cfg.part_size = v.parse().context("MEDIAGRAM_PART_SIZE")?;
    }
    if let Some(v) = env("DATA_DIR") {
        cfg.data_dir = Some(PathBuf::from(v));
    }
    if let Some(v) = env("SERVE_ADDR") {
        cfg.serve_addr = Some(v);
    }
    Ok(())
}
