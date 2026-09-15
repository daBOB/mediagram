//! Runtime configuration: TOML file overlaid by `MEDIAGRAM_*` environment
//! variables. Secrets (api_hash, tmdb_key) are never printed.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use mlib_spec::part_plan::{DEFAULT_PART_SIZE, validate_part_size};
use serde::Deserialize;

use crate::paths;

#[derive(Deserialize, Clone)]
pub struct Config {
    pub api_id: i32,
    pub api_hash: String,
    /// Channel id (`-100…`) or exact channel title.
    pub channel: String,
    pub tmdb_key: Option<String>,
    /// 32 random bytes, base64, shared with the player. Encrypts the
    /// prebuilt package; see `mediagram export-package`.
    pub package_key: Option<String>,
    /// Argv for publishing one file, with `{file}` standing in for its path,
    /// e.g. `["rclone", "copy", "{file}", "r2:mediagram/"]`. Run directly,
    /// never through a shell.
    pub publish_cmd: Option<Vec<String>>,
    /// Where the published files will be reachable, used to build the URL in
    /// `latest.json`. Not verified against where `publish_cmd` actually puts
    /// them.
    pub publish_base_url: Option<String>,
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

/// Manual Debug so api_hash, tmdb_key and package_key can never reach logs
/// or error chains.
impl std::fmt::Debug for Config {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Config")
            .field("api_id", &self.api_id)
            .field("api_hash", &"<redacted>")
            .field("channel", &self.channel)
            .field("tmdb_key", &self.tmdb_key.as_ref().map(|_| "<redacted>"))
            .field(
                "package_key",
                &self.package_key.as_ref().map(|_| "<redacted>"),
            )
            .field("publish_cmd", &self.publish_cmd)
            .field("publish_base_url", &self.publish_base_url)
            .field("part_size", &self.part_size)
            .field("throttle_ms", &self.throttle_ms)
            .field("max_attempts", &self.max_attempts)
            .field("tmp_dir", &self.tmp_dir)
            .field("data_dir", &self.data_dir)
            .finish()
    }
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
    // An empty `tmdb_key = ""` in the file means "not configured", same as an
    // empty MEDIAGRAM_TMDB_KEY; without this the add command's "set a key or
    // pass --manual" guard is bypassed and TMDB fails later with a bare 401.
    cfg.tmdb_key = cfg.tmdb_key.filter(|v| !v.is_empty());
    cfg.package_key = cfg.package_key.filter(|v| !v.is_empty());
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
    if let Some(v) = env("PACKAGE_KEY") {
        cfg.package_key = Some(v);
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

    #[test]
    fn empty_tmdb_key_in_file_loads_as_absent() {
        // A commented-out or blanked key must reach `add` as None so the
        // "set a key or pass --manual" guard fires instead of a TMDB 401.
        let mut f = tempfile::NamedTempFile::new().unwrap();
        std::io::Write::write_all(
            f.as_file_mut(),
            b"api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\ntmdb_key = \"\"\n",
        )
        .unwrap();
        let cfg = load(Some(f.path())).unwrap();
        assert!(cfg.tmdb_key.is_none());
    }

    #[test]
    fn non_empty_tmdb_key_in_file_is_kept() {
        let mut f = tempfile::NamedTempFile::new().unwrap();
        std::io::Write::write_all(
            f.as_file_mut(),
            b"api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\ntmdb_key = \"k3y\"\n",
        )
        .unwrap();
        let cfg = load(Some(f.path())).unwrap();
        assert_eq!(cfg.tmdb_key.as_deref(), Some("k3y"));
    }

    #[test]
    fn package_key_is_redacted_and_empty_loads_as_absent() {
        let mut f = tempfile::NamedTempFile::new().unwrap();
        std::io::Write::write_all(
            f.as_file_mut(),
            b"api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\npackage_key = \"\"\n",
        )
        .unwrap();
        assert!(load(Some(f.path())).unwrap().package_key.is_none());

        let cfg: Config = toml::from_str(
            "api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\npackage_key = \"c2VjcmV0\"\n",
        )
        .unwrap();
        let rendered = format!("{cfg:?}");
        assert!(!rendered.contains("c2VjcmV0"));
        assert!(rendered.contains("<redacted>"));
    }

    #[test]
    fn debug_output_redacts_secrets() {
        let cfg: Config = toml::from_str(
            "api_id = 1\napi_hash = \"sekrit\"\nchannel = \"c\"\ntmdb_key = \"k3y\"\n",
        )
        .unwrap();
        let dbg = format!("{cfg:?}");
        assert!(!dbg.contains("sekrit") && !dbg.contains("k3y"));
        assert!(dbg.contains("<redacted>"));
    }
}
