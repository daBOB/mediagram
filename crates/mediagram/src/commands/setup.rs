//! First run: `mediagram login` with no config file asks for what signing in
//! needs and writes one, instead of sending the user off to copy the example.

use std::io::Write;
use std::os::unix::fs::OpenOptionsExt;
use std::path::Path;

use anyhow::{Context, Result};
use dialoguer::Input;
use serde::Serialize;

use crate::config::{self, Config};

/// The keys a first run asks for: the three a config cannot load without,
/// plus the TMDB key, which `add` refuses to look a title up without. The
/// rest keep their defaults; `config.example.toml` documents them.
#[derive(Serialize)]
struct Initial<'a> {
    api_id: i32,
    api_hash: &'a str,
    channel: &'a str,
    /// Written even when it was skipped, empty, so the key to fill in later
    /// is visible in the file. An empty value loads as "not configured".
    tmdb_key: &'a str,
}

const HEADER: &str = "\
# Written by `mediagram login`. Every key can be overridden with
# MEDIAGRAM_<KEY> in the environment. The keys not asked for at login
# (part_size, data_dir, serve_addr, …) are documented in config.example.toml.
";

/// Prompts for api_id, api_hash, the channel and the TMDB key, writes
/// `path`, and returns the config loaded back from it. The phone number,
/// login code and 2FA password come after this, from the usual login flow.
pub fn run(path: &Path) -> Result<Config> {
    println!("No config at {}; setting one up.", path.display());
    println!("api_id and api_hash come from https://my.telegram.org.");
    let api_id: i32 = Input::new()
        .with_prompt("api_id")
        .interact_text()
        .context("reading api_id")?;
    let api_hash = prompt_nonempty("api_hash")?;
    let channel = prompt_nonempty("Channel (id like -1001234567890, or exact title)")?;
    // Optional: a library added entirely with `--manual` never needs one,
    // and it can be filled in later without touching anything else.
    let tmdb_key: String = Input::new()
        .with_prompt("tmdb_key from themoviedb.org (optional, Enter to skip)")
        .allow_empty(true)
        .interact_text()
        .context("reading tmdb_key")?;

    write(
        path,
        api_id,
        api_hash.trim(),
        channel.trim(),
        tmdb_key.trim(),
    )?;
    println!("Wrote {}", path.display());
    config::load(Some(path))
}

fn prompt_nonempty(prompt: &str) -> Result<String> {
    Input::<String>::new()
        .with_prompt(prompt)
        .validate_with(|input: &String| {
            if input.trim().is_empty() {
                Err("cannot be empty")
            } else {
                Ok(())
            }
        })
        .interact_text()
        .with_context(|| format!("reading {prompt}"))
}

/// Serializes through `toml` so a title holding quotes or backslashes comes
/// back out of the file unchanged.
fn write(path: &Path, api_id: i32, api_hash: &str, channel: &str, tmdb_key: &str) -> Result<()> {
    if let Some(dir) = path.parent() {
        // The file holds api_hash, so the directory it sits in is private too.
        crate::paths::ensure_private_dir(dir)?;
    }
    let body = toml::to_string(&Initial {
        api_id,
        api_hash,
        channel,
        tmdb_key,
    })
    .context("rendering config")?;
    // create_new: a config that appeared since the check is someone else's.
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .mode(0o600)
        .open(path)
        .with_context(|| format!("creating {}", path.display()))?;
    file.write_all(HEADER.as_bytes())
        .and_then(|()| file.write_all(body.as_bytes()))
        .with_context(|| format!("writing {}", path.display()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_env::EnvGuard;

    #[test]
    fn written_config_loads_back() {
        let _env_guard = EnvGuard::new();
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("mediagram/config.toml");
        write(&path, 12345, "0123456789abcdef", "-1001234567890", "k3y").unwrap();

        let cfg = config::load(Some(&path)).unwrap();
        assert_eq!(cfg.api_id, 12345);
        assert_eq!(cfg.api_hash, "0123456789abcdef");
        assert_eq!(cfg.channel, "-1001234567890");
        assert_eq!(cfg.tmdb_key.as_deref(), Some("k3y"));
        // Untouched keys keep their defaults rather than arriving empty.
        assert_eq!(cfg.tmdb_language, "en-US");
    }

    #[test]
    fn channel_title_with_quotes_survives_the_round_trip() {
        let _env_guard = EnvGuard::new();
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        write(&path, 1, "h", r#"Andre's "Backup\Films""#, "").unwrap();
        assert_eq!(
            config::load(Some(&path)).unwrap().channel,
            r#"Andre's "Backup\Films""#
        );
    }

    #[test]
    fn file_is_owner_only() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        write(&path, 1, "h", "c", "").unwrap();
        let mode = std::fs::metadata(&path).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600);
    }

    #[test]
    fn a_skipped_tmdb_key_is_written_empty_and_loads_as_absent() {
        let _env_guard = EnvGuard::new();
        // The key stays in the file so it is obvious where to put one later,
        // and `add`'s "set one or pass --manual" guard still fires.
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        write(&path, 1, "h", "c", "").unwrap();
        assert!(std::fs::read_to_string(&path).unwrap().contains("tmdb_key"));
        assert!(config::load(Some(&path)).unwrap().tmdb_key.is_none());
    }

    #[test]
    fn refuses_to_overwrite_an_existing_config() {
        let _env_guard = EnvGuard::new();
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        write(&path, 1, "h", "c", "").unwrap();
        assert!(write(&path, 2, "h2", "c2", "").is_err());
        assert_eq!(config::load(Some(&path)).unwrap().api_id, 1);
    }
}
