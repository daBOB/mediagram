//! The check that stands between a planned set and sending bytes for it: the
//! file it was planned against, still there and still the same size.

use std::path::{Path, PathBuf};

use anyhow::{Result, bail};

use crate::index::set_row::SetRow;

/// Validates only this set's local source, without touching the transport.
pub(super) async fn available_source(set: &SetRow, recorded: Option<&Path>) -> Result<PathBuf> {
    let source_path = recorded.ok_or_else(|| {
        anyhow::anyhow!(
            "set {} has no recorded source path; cannot resume",
            set.set_id
        )
    })?;
    let source_path = source_path.to_path_buf();
    let actual = match tokio::fs::metadata(&source_path).await {
        Ok(meta) => meta.len(),
        Err(err) => bail!(
            "source file for set {} is unavailable: {} ({err})",
            set.set_id,
            source_path.display()
        ),
    };
    if actual != set.total {
        bail!(
            "source file for set {} is {actual} bytes but the set was planned for {} bytes; it changed since add",
            set.set_id,
            set.total
        );
    }
    Ok(source_path)
}
