//! First run: `mediagram login` with no config file asks for what signing in
//! needs and writes one, instead of sending the user off to copy the example.

use std::io::Write;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;

use anyhow::{Context, Result};
use dialoguer::Input;
use serde::Serialize;

use crate::config::{self, Config};

/// The three keys a config cannot load without. Everything else keeps its
/// default; `config.example.toml` documents the rest.
#[derive(Serialize)]
struct Initial<'a> {
    api_id: i32,
    api_hash: &'a str,
    channel: &'a str,
}

const HEADER: &str = "\
# Written by `mediagram login`. Every key can be overridden with
# MEDIAGRAM_<KEY> in the environment. The optional keys (tmdb_key,
# part_size, data_dir, serve_addr, …) are documented in config.example.toml.
";

/// Prompts for api_id, api_hash and the channel, writes `path`, and returns
/// the config loaded back from it. The phone number, login code and 2FA
/// password come after this, from the usual login flow.
pub fn run(path: &Path) -> Result<Config> {
    println!("No config at {}; setting one up.", path.display());
    println!("api_id and api_hash come from https://my.telegram.org.");
    let api_id: i32 = Input::new()
        .with_prompt("api_id")
        .interact_text()
        .context("reading api_id")?;
    let api_hash = prompt_nonempty("api_hash")?;
    let channel = prompt_nonempty("Channel (id like -1001234567890, or exact title)")?;

    write(path, api_id, api_hash.trim(), channel.trim())?;
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
fn write(path: &Path, api_id: i32, api_hash: &str, channel: &str) -> Result<()> {
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)
            .with_context(|| format!("creating config dir {}", dir.display()))?;
        // The file holds api_hash, so the directory it sits in is private too.
        std::fs::set_permissions(dir, std::fs::Permissions::from_mode(0o700))
            .with_context(|| format!("restricting permissions on {}", dir.display()))?;
    }
    let body = toml::to_string(&Initial {
        api_id,
        api_hash,
        channel,
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

    #[test]
    fn written_config_loads_back() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("mediagram/config.toml");
        write(&path, 12345, "0123456789abcdef", "-1001234567890").unwrap();

        let cfg = config::load(Some(&path)).unwrap();
        assert_eq!(cfg.api_id, 12345);
        assert_eq!(cfg.api_hash, "0123456789abcdef");
        assert_eq!(cfg.channel, "-1001234567890");
        // Untouched keys keep their defaults rather than arriving empty.
        assert!(cfg.tmdb_key.is_none());
        assert_eq!(cfg.tmdb_language, "en-US");
    }

    #[test]
    fn channel_title_with_quotes_survives_the_round_trip() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        write(&path, 1, "h", r#"Andre's "Backup\Films""#).unwrap();
        assert_eq!(
            config::load(Some(&path)).unwrap().channel,
            r#"Andre's "Backup\Films""#
        );
    }

    #[test]
    fn file_is_owner_only() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        write(&path, 1, "h", "c").unwrap();
        let mode = std::fs::metadata(&path).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600);
    }

    #[test]
    fn refuses_to_overwrite_an_existing_config() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        write(&path, 1, "h", "c").unwrap();
        assert!(write(&path, 2, "h2", "c2").is_err());
        assert_eq!(config::load(Some(&path)).unwrap().api_id, 1);
    }
}
