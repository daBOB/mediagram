//! The staging directory: a read-only copy of the index, the posters, and
//! the manifest, assembled on disk before being archived and encrypted.
//!
//! Everything here is plaintext holding the private channel id and every
//! message id, so the directory is 0700 and its files 0600, matching how the
//! session file is already protected, and it is removed whether the export
//! succeeds or fails.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use mediagram_tmdb::poster_files::download_into;
use mediagram_tmdb::posters::PosterRef;
use mlib_spec::package::{PackageManifest, PosterEntry};
use rusqlite::Connection;

use crate::index::snapshot;
use crate::paths::{ensure_private_dir, restrict_file, write_private};

pub const POSTER_DIR: &str = "posters";
pub const MANIFEST_FILE: &str = "manifest.json";

/// A staging directory that deletes itself when dropped, so a failure part
/// way through never leaves a decrypted copy of the library on disk.
pub struct Staging {
    path: PathBuf,
}

impl Staging {
    /// Creates `<parent>/<name>` with owner-only permissions.
    pub fn create(parent: &Path, name: &str) -> Result<Staging> {
        let path = parent.join(name);
        if path.exists() {
            std::fs::remove_dir_all(&path)
                .with_context(|| format!("clearing stale staging dir {}", path.display()))?;
        }
        // Staged files hold the private channel id and every message id.
        ensure_private_dir(&path)?;
        Ok(Staging { path })
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    /// Copies the index without touching the live database.
    ///
    /// Deliberately no WAL checkpoint first. `push-index` checkpoints because
    /// it is about to write anyway, but a checkpoint folds WAL pages into the
    /// main file, which changes it. `VACUUM INTO` reads through an
    /// uncheckpointed WAL on its own, so skipping it is both correct and the
    /// only way this stays a read.
    pub fn copy_index(&self, conn: &Connection) -> Result<u64> {
        let dest = self.path.join(mlib_spec::schema::INDEX_FILE);
        snapshot::copy_to(conn, &dest)?;
        restrict_file(&dest)?;
        Ok(std::fs::metadata(&dest)
            .with_context(|| format!("sizing {}", dest.display()))?
            .len())
    }

    /// Stages the artwork, and says where each poster ended up.
    ///
    /// The manifest names files by their path inside the archive, so the
    /// directory prefix is added here: `download_into` knows about keys and
    /// bytes, and nothing about the package layout.
    pub async fn fetch_posters(
        &self,
        http: &reqwest::Client,
        refs: &[PosterRef],
    ) -> Result<Vec<PosterEntry>> {
        let keys = download_into(http, refs, &self.path.join(POSTER_DIR)).await?;
        Ok(keys
            .into_iter()
            .map(|key| PosterEntry {
                file: format!("{POSTER_DIR}/{key}.jpg"),
                key,
            })
            .collect())
    }

    /// Writes the manifest last, once the counts are known.
    pub fn write_manifest(&self, manifest: &PackageManifest) -> Result<()> {
        let dest = self.path.join(MANIFEST_FILE);
        let text = serde_json::to_vec(manifest).context("serializing the manifest")?;
        write_private(&dest, &text)
    }
}

impl Drop for Staging {
    fn drop(&mut self) {
        if let Err(err) = std::fs::remove_dir_all(&self.path) {
            if err.kind() != std::io::ErrorKind::NotFound {
                tracing::warn!(path = %self.path.display(), error = %err, "staging dir left behind");
            }
        }
    }
}
