//! The byte half of an upload: sending what is left of one planned set.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use rusqlite::Connection;

use crate::index::set_row::SetRow;
use crate::index::{db, parts};
use crate::upload::pipeline::run_set;
use crate::upload::transport::Transport;

/// Validates only this set's local source, without touching the transport.
pub(super) async fn available_source(set: &SetRow, recorded: Option<&str>) -> Result<PathBuf> {
    let source_path = recorded.ok_or_else(|| {
        anyhow::anyhow!(
            "set {} has no recorded source path; cannot resume",
            set.set_id
        )
    })?;
    let source_path = PathBuf::from(source_path);
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

pub(super) async fn finish_from(
    conn: &Connection,
    transport: &impl Transport,
    throttle_ms: u64,
    set: &SetRow,
    data_dir: &Path,
    source_path: &Path,
) -> Result<bool> {
    run_set(
        conn,
        transport,
        throttle_ms,
        set,
        source_path,
        Some(data_dir),
    )
    .await
    .with_context(|| format!("uploading set {}", set.set_id))?;

    let complete = parts::pending_parts(conn, &set.set_id)?.is_empty();
    if complete {
        db::delete_meta(conn, &db::source_key(&set.set_id))?;
    }
    Ok(complete)
}
