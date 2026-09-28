//! Finishes one set: uploads every part still `pending`, adopting parts that
//! already reached the channel from a previous, interrupted run, then marks
//! the set `complete` once nothing is left pending.

use std::collections::HashMap;
use std::path::Path;
use std::time::{Duration, Instant};

use anyhow::Result;
use rusqlite::Connection;

use super::part_upload::SetUpload;
use super::progress;
use super::transport::Transport;
use crate::index::set_row::SetRow;
use crate::index::{lifecycle, parts};
use crate::upload::adopt::adoption_map;

/// Finishes `set`: uploads/adopts every pending part in idx order, then
/// finalizes the set once none remain. Safe to call again on a set that is
/// already fully `done` (e.g. a crash between the last part and finalizing).
/// Returns whether the set is now complete; completing it is the last thing
/// that can fail, so an error always means it is not.
pub async fn run_set<T: Transport>(
    conn: &Connection,
    transport: &T,
    throttle_ms: u64,
    set: &SetRow,
    source_path: &Path,
    data_dir: Option<&Path>,
) -> Result<bool> {
    let template = set.caption_template();
    let total_parts = set.part_count;

    let pending = parts::pending_parts(conn, &set.set_id)?;
    let adopted = if pending.is_empty() {
        HashMap::new()
    } else {
        let scan_limit = 3 * total_parts as usize;
        adoption_map(&set.set_id, &transport.recent_messages(scan_limit).await?)
    };

    // Said before the first byte moves: connecting, resolving and hashing all
    // happen before anything visible would otherwise, and a command that
    // prints nothing for minutes looks wedged rather than busy.
    if !pending.is_empty() {
        println!(
            "uploading {} · {:.2} GB in {}",
            template.display_name(),
            set.total as f64 / 1e9,
            if total_parts == 1 {
                "1 part".to_string()
            } else {
                format!("{total_parts} parts")
            },
        );
    }

    let upload = SetUpload {
        transport,
        template: &template,
        set,
        source_path,
        started: Instant::now(),
        data_dir,
    };
    // Parts already in the channel before this run, so a resumed upload
    // reports against the whole set rather than against what it did today.
    let mut bytes_done: u64 = parts::done_bytes(conn, &set.set_id).unwrap_or(0);
    for part in pending {
        let landed = match adopted.get(&part.idx) {
            Some(found) => {
                tracing::info!(idx = part.idx, total = total_parts, "adopted existing part");
                parts::Landed {
                    chat_id: transport.chat_id(),
                    message_id: found.message_id,
                    doc_id: found.doc_id,
                    sha256: found.sha256.clone(),
                }
            }
            None => upload.send(&part, bytes_done).await?,
        };
        parts::mark_done(conn, &set.set_id, part.idx, &landed)?;

        bytes_done = bytes_done.saturating_add(part.byte_length);
        if throttle_ms > 0 {
            tokio::time::sleep(Duration::from_millis(throttle_ms)).await;
        }
    }

    if !parts::pending_parts(conn, &set.set_id)?.is_empty() {
        return Ok(false);
    }
    let hash = mlib_spec::set_hash::set_hash(&parts::done_hashes(conn, &set.set_id)?);
    let completed = lifecycle::complete(conn, &set.set_id, &hash)?;
    if let Some(temp) = completed.temp {
        // Already forgotten by the index. The remux sits in the configured
        // temp directory, or beside the original when none is set, so a
        // delete that fails is said with the path, for deleting by hand.
        if let Err(err) = tokio::fs::remove_file(&temp).await {
            tracing::warn!(path = %temp.display(), error = %err, "failed to remove the faststart remux; delete it by hand");
        }
    }
    // Nothing is being uploaded any more, and a note left behind would
    // describe a set that is finished.
    if let Some(dir) = data_dir {
        progress::clear(dir, &set.set_id);
    }
    Ok(true)
}
