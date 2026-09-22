//! XDG locations. Config is human-edited; data holds the session, library.db
//! and the TMDB response cache.

use std::io::Write;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use directories::ProjectDirs;

fn dirs() -> Result<ProjectDirs> {
    ProjectDirs::from("", "", "mediagram").context("cannot determine home directory")
}

pub fn config_file() -> Result<PathBuf> {
    Ok(dirs()?.config_dir().join("config.toml"))
}

pub fn data_dir() -> Result<PathBuf> {
    Ok(dirs()?.data_dir().to_path_buf())
}

/// Creates `dir` (and its parents) and makes it owner-only.
///
/// Everything the uploader keeps — the session and its WAL sidecars, the
/// config holding `api_hash`, the index with the private channel id and a
/// staged export of it — is one account's, so every directory holding one
/// is closed to other users, not just the files in it.
pub fn private_dir(dir: &Path) -> Result<()> {
    std::fs::create_dir_all(dir).with_context(|| format!("creating {}", dir.display()))?;
    std::fs::set_permissions(dir, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting {}", dir.display()))
}

/// Writes `bytes` to a file created owner-only, so it is never readable by
/// anyone else, not even between being written and being restricted.
pub fn write_private(path: &Path, bytes: &[u8]) -> Result<()> {
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(path)
        .with_context(|| format!("creating {}", path.display()))?;
    // `mode` applies only when the file is created; one left by an earlier
    // run keeps whatever it had.
    restrict_file(path)?;
    file.write_all(bytes).with_context(|| format!("writing {}", path.display()))
}

/// Makes a file someone else created — SQLite, libsql — owner-only.
pub fn restrict_file(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
        .with_context(|| format!("restricting {}", path.display()))
}
