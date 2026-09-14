//! XDG locations. Config is human-edited; data holds the session, library.db
//! and the TMDB response cache.

use std::path::PathBuf;

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
