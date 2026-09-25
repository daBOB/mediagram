//! Runtime configuration: TOML file overlaid by `MEDIAGRAM_CACHE_*`
//! environment variables, following the same pattern as `mediagram`'s own
//! config.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use directories::ProjectDirs;
use serde::Deserialize;

pub const DEFAULT_LISTEN: &str = "0.0.0.0:7788";
pub const DEFAULT_BUDGET_BYTES: u64 = 64 * 1024 * 1024 * 1024;

#[derive(Deserialize, Clone, Debug)]
pub struct Config {
    #[serde(default = "default_listen")]
    pub listen: String,
    #[serde(default = "default_root")]
    pub root: PathBuf,
    #[serde(default = "default_budget")]
    pub budget: u64,
    #[serde(default = "default_mdns")]
    pub mdns: bool,
    /// Where the pairing token lives — separate from `root` so clearing the
    /// cache does not unpair every device.
    #[serde(default = "default_state_dir")]
    pub state_dir: PathBuf,
}

/// Panics only when the OS cannot say where a user's home is — the same
/// condition `mediagram`'s own `paths::data_dir` treats as fatal, just
/// without a `Result` to carry it through: a `serde(default = ...)`
/// function's signature has no room for one. `$CACHE_DIRECTORY` and
/// `$STATE_DIRECTORY` (systemd sets both) sidestep it entirely, which is
/// how this server actually runs in production.
fn project_dirs() -> ProjectDirs {
    ProjectDirs::from("", "", "mediagram-cache").expect("cannot determine the home directory")
}

fn default_listen() -> String {
    DEFAULT_LISTEN.to_string()
}

/// `$CACHE_DIRECTORY` (systemd's `CacheDirectory=` sets this) or
/// `~/.cache/mediagram-cache`.
fn default_root() -> PathBuf {
    std::env::var_os("CACHE_DIRECTORY")
        .map(PathBuf::from)
        .unwrap_or_else(|| project_dirs().cache_dir().to_path_buf())
}

fn default_budget() -> u64 {
    DEFAULT_BUDGET_BYTES
}

fn default_mdns() -> bool {
    true
}

/// `$STATE_DIRECTORY` (systemd's `StateDirectory=` sets this) or
/// `~/.local/state/mediagram-cache`.
fn default_state_dir() -> PathBuf {
    std::env::var_os("STATE_DIRECTORY")
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            let dirs = project_dirs();
            dirs.state_dir()
                .map(Path::to_path_buf)
                .unwrap_or_else(|| dirs.data_dir().to_path_buf())
        })
}

/// Loads `path` (defaulting to every field's built-in default when there is
/// no file at all — this server needs no secret to start, unlike
/// `mediagram`'s config), then applies `MEDIAGRAM_CACHE_*` overrides.
pub fn load(path: Option<&Path>) -> Result<Config> {
    let mut cfg: Config = match path {
        Some(p) => {
            let text = std::fs::read_to_string(p)
                .with_context(|| format!("cannot read config {}", p.display()))?;
            toml::from_str(&text).with_context(|| format!("invalid config {}", p.display()))?
        }
        None => toml::from_str("").expect("an empty document fills every field from its default"),
    };
    apply_env(&mut cfg)?;
    Ok(cfg)
}

fn apply_env(cfg: &mut Config) -> Result<()> {
    let env = |k: &str| {
        std::env::var(format!("MEDIAGRAM_CACHE_{k}"))
            .ok()
            .filter(|v| !v.is_empty())
    };
    if let Some(v) = env("LISTEN") {
        cfg.listen = v;
    }
    if let Some(v) = env("ROOT") {
        cfg.root = PathBuf::from(v);
    }
    if let Some(v) = env("BUDGET") {
        cfg.budget = v.parse().context("MEDIAGRAM_CACHE_BUDGET")?;
    }
    if let Some(v) = env("MDNS") {
        cfg.mdns = v.parse().context("MEDIAGRAM_CACHE_MDNS")?;
    }
    Ok(())
}

#[cfg(test)]
#[path = "config_tests.rs"]
mod tests;
