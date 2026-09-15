//! The staging directory: a read-only copy of the index, the posters, and
//! the manifest, assembled on disk before being archived and encrypted.
//!
//! Everything here is plaintext holding the private channel id and every
//! message id, so the directory is 0700 and its files 0600, matching how the
//! session file is already protected, and it is removed whether the export
//! succeeds or fails.

use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use std::time::Duration;

use anyhow::{Context, Result, bail};
use mlib_spec::package::{PackageManifest, PosterEntry};
use rusqlite::Connection;

use crate::export::posters::{PosterRef, poster_url};
use crate::index::snapshot;

pub const INDEX_FILE: &str = "library.db";
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
        std::fs::create_dir_all(&path)
            .with_context(|| format!("creating staging dir {}", path.display()))?;
        std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o700))
            .with_context(|| format!("restricting {}", path.display()))?;
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
        let dest = self.path.join(INDEX_FILE);
        snapshot::copy_to(conn, &dest)?;
        restrict(&dest)?;
        Ok(std::fs::metadata(&dest)
            .with_context(|| format!("sizing {}", dest.display()))?
            .len())
    }

    /// Downloads each poster into `posters/`. A poster that will not download
    /// is skipped: the catalog is the product, the artwork is a convenience.
    pub async fn fetch_posters(
        &self,
        http: &reqwest::Client,
        refs: &[PosterRef],
    ) -> Result<Vec<PosterEntry>> {
        if refs.is_empty() {
            return Ok(Vec::new());
        }
        let dir = self.path.join(POSTER_DIR);
        std::fs::create_dir_all(&dir).with_context(|| format!("creating {}", dir.display()))?;
        std::fs::set_permissions(&dir, std::fs::Permissions::from_mode(0o700))
            .with_context(|| format!("restricting {}", dir.display()))?;

        let mut entries = Vec::new();
        for poster in refs {
            // The key becomes a file name, a manifest path and a tar member
            // name, so it is checked here rather than trusted from upstream.
            if !mlib_spec::package::poster_key_is_valid(&poster.key) {
                tracing::warn!(key = %poster.key, "poster key rejected");
                continue;
            }
            let file = format!("{}/{}.jpg", POSTER_DIR, poster.key);
            match download(http, &poster_url(&poster.path), &self.path.join(&file)).await {
                Ok(()) => entries.push(PosterEntry {
                    key: poster.key.clone(),
                    file,
                }),
                Err(err) => {
                    tracing::warn!(key = %poster.key, error = %err, "poster skipped");
                }
            }
        }
        Ok(entries)
    }

    /// Writes the manifest last, once the counts are known.
    pub fn write_manifest(&self, manifest: &PackageManifest) -> Result<()> {
        let dest = self.path.join(MANIFEST_FILE);
        let text = serde_json::to_vec(manifest).context("serializing the manifest")?;
        std::fs::write(&dest, text).with_context(|| format!("writing {}", dest.display()))?;
        restrict(&dest)
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

/// A poster that will not arrive promptly is not worth stalling an export
/// for, and a body that keeps coming is not worth buffering.
const POSTER_TIMEOUT: Duration = Duration::from_secs(20);
const POSTER_MAX_BYTES: u64 = 4 * 1024 * 1024;

async fn download(http: &reqwest::Client, url: &str, dest: &Path) -> Result<()> {
    let response = http
        .get(url)
        .timeout(POSTER_TIMEOUT)
        .send()
        .await
        .context("requesting poster")?;
    let response = response
        .error_for_status()
        .context("poster request failed")?;
    if let Some(len) = response.content_length()
        && len > POSTER_MAX_BYTES
    {
        bail!("poster is {len} bytes, over the {POSTER_MAX_BYTES} byte limit");
    }
    let bytes = response.bytes().await.context("reading poster body")?;
    if bytes.len() as u64 > POSTER_MAX_BYTES {
        bail!("poster body exceeded the {POSTER_MAX_BYTES} byte limit");
    }
    std::fs::write(dest, &bytes).with_context(|| format!("writing {}", dest.display()))?;
    restrict(dest)
}

fn restrict(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
        .with_context(|| format!("restricting {}", path.display()))
}
