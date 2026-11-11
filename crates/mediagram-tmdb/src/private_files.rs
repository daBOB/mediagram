//! Owner-only files and directories for artwork.

use std::io::Write;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;

use anyhow::{Context, Result};

/// Artwork is written for one account's library and nobody else's, so a
/// poster is created owner-only rather than restricted after it lands.
///
/// Written beside `path` and renamed into place: an image that is replaced
/// may be on screen or mid-read, and must never be seen half-written. The
/// process id keeps two runs from sharing one temporary file.
pub(crate) fn write_private(path: &Path, bytes: &[u8]) -> Result<()> {
    let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("image");
    let tmp = path.with_file_name(format!(".{name}.{}.tmp", std::process::id()));
    let written = (|| -> Result<()> {
        // The temporary file is always new to this call, so `mode` applies.
        let mut file = std::fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .open(&tmp)
            .with_context(|| format!("creating {}", tmp.display()))?;
        file.write_all(bytes)
            .with_context(|| format!("writing {}", tmp.display()))?;
        std::fs::rename(&tmp, path).with_context(|| format!("replacing {}", path.display()))
    })();
    if written.is_err() {
        let _ = std::fs::remove_file(&tmp);
    }
    written
}

/// The same, for a directory, which needs the execute bit to be enterable.
pub(crate) fn restrict_dir(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting {}", path.display()))
}
