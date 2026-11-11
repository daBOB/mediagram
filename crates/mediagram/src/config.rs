//! Runtime configuration: TOML file overlaid by `MEDIAGRAM_*` environment
//! variables. Secrets (api_hash, tmdb_key) are never printed.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Context, Result, bail};
use mediagram_tmdb::disk_cache::DiskCachedApi;
use mediagram_tmdb::home_cache::HomeCache;
use mediagram_tmdb::localized::Localized;
use mediagram_tmdb::tmdb_client::TmdbClient;
use mlib_spec::part_plan::{DEFAULT_PART_SIZE, validate_part_size};
use serde::Deserialize;

use crate::paths;

mod env;
use env::apply_env;

#[derive(Deserialize, Clone)]
pub struct Config {
    pub api_id: i32,
    pub api_hash: String,
    /// Channel id (`-100…`) or exact channel title.
    pub channel: String,
    pub tmdb_key: Option<String>,
    /// Which language TMDB should answer in, as a BCP-47 tag like `de-DE`.
    ///
    /// TMDB answers in English unless asked, so a German library gets
    /// "Forsaken" where the file says "Verlassen". Defaults to `en-US`, which
    /// is what TMDB does anyway, so an existing install is unaffected.
    #[serde(default = "default_tmdb_language")]
    pub tmdb_language: String,
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
    /// Uploads at once across processes; Telegram limits speed per connection.
    #[serde(default = "default_upload_slots")]
    pub upload_slots: usize,
    /// Retry attempts for transient Telegram/network errors.
    #[serde(default = "default_max_attempts")]
    pub max_attempts: u32,
    /// Where faststart remux output goes; default: next to the source file.
    pub tmp_dir: Option<PathBuf>,
    /// Session, library.db, tmdb cache; default: XDG data dir.
    pub data_dir: Option<PathBuf>,
    /// Where `mediagram serve` listens; default: loopback, see
    /// `commands::serve::DEFAULT_ADDR`.
    pub serve_addr: Option<String>,
    /// The household's cache server, e.g. `http://127.0.0.1:7788`. TMDB
    /// answers and artwork are read from and filled into it, so the web
    /// player and the TV app share one cache. Unset or unreachable: skipped.
    pub cache_server: Option<String>,
    /// The key that signs uploads to `cache_server`. Only signatures made
    /// with it are sent, never the key itself.
    pub cache_server_token: Option<String>,
    /// The file this was read from. Not a key: `load` records it so a
    /// process this one starts can be given the same config, which the
    /// environment alone does not carry.
    #[serde(skip)]
    pub loaded_from: Option<PathBuf>,
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
            .field("tmdb_language", &self.tmdb_language)
            .field(
                "package_key",
                &self.package_key.as_ref().map(|_| "<redacted>"),
            )
            .field("publish_cmd", &self.publish_cmd)
            .field("publish_base_url", &self.publish_base_url)
            .field("part_size", &self.part_size)
            .field("throttle_ms", &self.throttle_ms)
            .field("upload_slots", &self.upload_slots)
            .field("max_attempts", &self.max_attempts)
            .field("tmp_dir", &self.tmp_dir)
            .field("data_dir", &self.data_dir)
            .field("serve_addr", &self.serve_addr)
            .field("cache_server", &self.cache_server)
            .field(
                "cache_server_token",
                &self.cache_server_token.as_ref().map(|_| "<redacted>"),
            )
            .field("loaded_from", &self.loaded_from)
            .finish()
    }
}

fn default_tmdb_language() -> String {
    "en-US".to_string()
}

fn default_part_size() -> u64 {
    DEFAULT_PART_SIZE
}
fn default_upload_slots() -> usize {
    1
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

    /// The TMDB client every command uses, over `http` — passed in so a
    /// command that also downloads artwork uses one client for both.
    ///
    /// Built even with no key configured: the payloads `add` looked up are
    /// cached under the data directory, and a warm cache answers without one.
    /// With no key, what neither that cache nor the cache server holds is an
    /// error, never a request TMDB would refuse.
    pub fn tmdb_client(
        &self,
        http: reqwest::Client,
    ) -> Result<Localized<DiskCachedApi<Option<TmdbClient>>>> {
        let home = self.home_cache(&http);
        let key = self.tmdb_key.as_deref().filter(|k| !k.is_empty());
        let disk = DiskCachedApi::new(key.map(|k| TmdbClient::new(http, k)), &self.data_dir()?)
            .with_home_cache(home);
        Ok(Localized::new(disk, &self.tmdb_language))
    }

    /// The cache server named in the config, if any. An empty value counts
    /// as unset, like the other optional keys.
    pub fn home_cache(&self, http: &reqwest::Client) -> Option<Arc<HomeCache>> {
        let url = self.cache_server.as_deref().filter(|u| !u.is_empty())?;
        let token = self.cache_server_token.clone().filter(|t| !t.is_empty());
        Some(Arc::new(HomeCache::new(http.clone(), url, token)))
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
            "cannot read config {}; run `mediagram login` to create one, or copy config.example.toml there",
            path.display()
        )
    })?;
    let mut cfg: Config =
        toml::from_str(&text).with_context(|| format!("invalid config {}", path.display()))?;
    cfg.loaded_from = Some(path.clone());
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

#[cfg(test)]
#[path = "config_tests.rs"]
mod tests;
