//! Exporting the prebuilt metadata package: staging, posters, archive,
//! encryption. The only command-facing entry point is
//! `commands::export_package`.

pub mod archive;
pub mod budget;
pub mod latest;
pub mod pointer;
pub mod publish;
pub mod stage;
pub mod titles;

use std::os::unix::fs::PermissionsExt;
use std::path::Path;

use anyhow::{Context, Result};

/// Owner-only, for every file this module writes.
///
/// Staged files hold the private channel id and every message id. The local
/// poster directory holds neither, but it sits beside the index that does,
/// and one rule for the whole module is easier to keep than two.
pub(crate) fn restrict(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
        .with_context(|| format!("restricting {}", path.display()))
}

/// The same, for a directory, which needs the execute bit to be enterable.
pub(crate) fn restrict_dir(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting {}", path.display()))
}
