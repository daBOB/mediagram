//! The catalog as versions on disk, and how one becomes current.
//!
//! Layout under the catalog root (`<data_dir>/catalog/`): version
//! directories (`v-<pushed_at>`, one per install), a `current` symlink
//! pointing at the one in use, and `incoming/`, where the next one is
//! assembled. Fetched artwork and descriptions live beside these, never
//! inside a version, so an install — which clears every other version —
//! keeps them.
//!
//! `install` is the only thing that ever writes a version; readers only
//! ever open one through `current`, so an install landing mid-query cannot
//! be observed as a half-written database.

pub mod identity;
mod install;

use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use rusqlite::{Connection, OpenFlags};

use crate::catalog as queries;
use crate::error::CoreError;

pub use install::{Staging, install_staged};

pub const CURRENT: &str = "current";
pub const MANIFEST_FILE: &str = "manifest.json";

/// How far ahead of now a catalog may claim to have been built. Clocks
/// disagree by minutes, not days; a catalog dated next year is either a
/// mistake or an attempt to make every later one look stale.
pub const FUTURE_TOLERANCE_SECONDS: i64 = 24 * 60 * 60;

/// The version `current` points at, through the symlink.
pub fn current(root: &Path) -> PathBuf {
    root.join(CURRENT)
}

pub fn library_db(dir: &Path) -> PathBuf {
    dir.join(mlib_spec::schema::INDEX_FILE)
}

/// Opens a database read-only: nothing under the catalog root is this
/// crate's index to write, and a writable handle could checkpoint the WAL of
/// a version an install is about to remove.
pub fn open_ro(path: &Path) -> Result<Connection, CoreError> {
    Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(CoreError::io("opening the catalog"))
}

/// How many playable sets the version at `dir` holds.
pub fn count_playable(dir: &Path) -> Result<u64, CoreError> {
    let conn = open_ro(&library_db(dir))?;
    let sets = queries::list_playable(&conn).map_err(CoreError::io("reading the catalog"))?;
    Ok(sets.len() as u64)
}

/// When the version named `name` was pushed.
///
/// Every version is named `v-<pushed_at>` — with a `-<n>` suffix when the
/// same version is installed again beside itself — so the catalog's age is
/// already written down and needs no second record that could disagree with
/// it. A name that is not one of ours reads as unknown rather than as a
/// wrong date.
pub fn pushed_at_of(name: &str) -> Option<i64> {
    name.strip_prefix("v-")?.split('-').next()?.parse().ok()
}

pub fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}
