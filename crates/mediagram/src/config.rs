//! Runtime configuration: TOML file overlaid by `MEDIAGRAM_*` environment
//! variables. Secrets (api_hash, tmdb_key) are never printed.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use mlib_spec::part_plan::{DEFAULT_PART_SIZE, validate_part_size};
use serde::Deserialize;

use crate::paths;

#[derive(Deserialize, Debug, Clone)]
pub struct Config {
    pub api_id: i32,
    pub api_hash: String,
    /// Channel id (`-100…`) or exact channel title.
    pub channel: String,
    pub tmdb_key: Option<String>,
    #[serde(default = "default_part_size")]
    pub part_size: u64,
    /// Pause between part uploads, to stay clear of flood limits.
    #[serde(default)]
    pub throttle_ms: u64,
    /// Retry attempts for transient Telegram/network errors.
    #[serde(default = "default_max_attempts")]
    pub max_attempts: u32,
    /// Where faststart remux output goes; default: next to the source file.
    pub tmp_dir: Option<PathBuf>,
    /// Session, library.db, tmdb cache; default: XDG data dir.
    pub data_dir: Option<PathBuf>,
}

fn default_part_size() -> u64 {
    DEFAULT_PART_SIZE
}
fn default_max_attempts() -> u32 {
    5
}

impl Config {
    pub fn data_dir(&self) -> Result<PathBuf> {
        match &self.data_dir {
            Some(d) => Ok(d.clone()),
            None => paths::data_dir(),
        }
    }
}

/// Load from `path` (or the XDG default), apply env overrides, validate.
pub fn load(path: Option<&Path>) -> Result<Config> {
    let path = match path {
        Some(p) => p.to_path_buf(),
        None => paths::config_file()?,
    };
    let text = std::fs::read_to_string(&path).with_context(|| {
        format!(
            "cannot read config {}; copy config.example.toml there first",
            path.display()
        )
    })?;
    let mut cfg: Config =
        toml::from_str(&text).with_context(|| format!("invalid config {}", path.display()))?;
    apply_env(&mut cfg)?;
    validate_part_size(cfg.part_size).context("part_size")?;
    if cfg.api_hash.is_empty() || cfg.channel.is_empty() {
        bail!("api_hash and channel must be set");
    }
    Ok(cfg)
}

fn apply_env(cfg: &mut Config) -> Result<()> {
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
    if let Some(v) = env("PART_SIZE") {
        cfg.part_size = v.parse().context("MEDIAGRAM_PART_SIZE")?;
    }
    if let Some(v) = env("DATA_DIR") {
        cfg.data_dir = Some(PathBuf::from(v));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_minimal_toml_with_defaults() {
        let cfg: Config =
            toml::from_str("api_id = 1\napi_hash = \"h\"\nchannel = \"Library\"\n").unwrap();
        assert_eq!(cfg.part_size, DEFAULT_PART_SIZE);
        assert_eq!(cfg.max_attempts, 5);
        assert!(cfg.tmdb_key.is_none());
    }
}
