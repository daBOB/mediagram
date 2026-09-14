//! Finishes one set: uploads every part still `pending`, adopting parts that
//! already reached the channel from a previous, interrupted run, then marks
//! the set `complete` once nothing is left pending.

use std::collections::HashMap;
use std::path::Path;
use std::time::{Duration, Instant};

use anyhow::Result;
use mlib_spec::caption::{Caption, Part};
use rusqlite::Connection;

use super::part_reader::PartReader;
use super::transport::Transport;
use crate::index::sets::SetRow;
use crate::index::{db, parts, sets};
use crate::upload::adopt::adoption_map;

/// Finishes `set`: uploads/adopts every pending part in idx order, then
/// finalizes the set once none remain. Safe to call again on a set that is
/// already fully `done` (e.g. a crash between the last part and finalizing).
pub async fn run_set<T: Transport>(
    conn: &Connection,
    transport: &T,
    throttle_ms: u64,
    set: &SetRow,
    source_path: &Path,
) -> Result<()> {
    let template = set.caption_template()?;
    let total_parts = set.part_count;

    let pending = parts::pending_parts(conn, &set.set_id)?;
    let adopted = if pending.is_empty() {
        HashMap::new()
    } else {
        let scan_limit = 3 * total_parts as usize;
        adoption_map(&set.set_id, &transport.recent_messages(scan_limit).await?)
    };

    let started = Instant::now();
    for part in pending {
        let (message_id, doc_id, sha256) = match adopted.get(&part.idx) {
            Some(found) => {
                tracing::info!(idx = part.idx, total = total_parts, "adopted existing part");
                (found.message_id, found.doc_id, found.sha256.clone())
            }
            None => {
                upload_one(
                    transport,
                    &template,
                    set,
                    &part,
                    total_parts,
                    source_path,
                    started,
                )
                .await?
            }
        };
        parts::mark_done(
            conn,
            &set.set_id,
            part.idx,
            transport.chat_id(),
            message_id,
            doc_id,
            &sha256,
        )?;

        if throttle_ms > 0 {
            tokio::time::sleep(Duration::from_millis(throttle_ms)).await;
        }
    }

    if parts::pending_parts(conn, &set.set_id)?.is_empty() {
        let hash = mlib_spec::set_hash::set_hash(&parts::done_hashes(conn, &set.set_id)?);
        sets::set_hash_and_complete(conn, &set.set_id, &hash)?;
        remove_recorded_tmp(conn, &set.set_id).await;
    }

    Ok(())
}

/// Streams one part through `transport`, hashing as it goes, and returns
/// the ids and hash to record via `mark_done`.
#[allow(clippy::too_many_arguments)]
async fn upload_one<T: Transport>(
    transport: &T,
    template: &Caption,
    set: &SetRow,
    part: &parts::PartRow,
    total_parts: u32,
    source_path: &Path,
    started: Instant,
) -> Result<(i64, i64, String)> {
    let mut reader = PartReader::open(source_path, part.byte_offset, part.byte_length)
        .await
        .map_err(|err| {
            anyhow::anyhow!(
                "opening part {} of {}: {err}",
                part.idx,
                source_path.display()
            )
        })?;
    let base_name = mlib_spec::part_name::base_name(template);
    let name =
        mlib_spec::part_name::part_file_name(&base_name, &set.container, part.idx, total_parts);
    let caption = template.with_part(Part {
        i: part.idx,
        n: total_parts,
        off: part.byte_offset,
        len: part.byte_length,
        sha256: String::new(),
    });
    let human = format!(
        "{} — part {}/{}",
        template.display_name(),
        part.idx + 1,
        total_parts
    );

    let sent = transport
        .send_part(
            name,
            mime_for(&set.container),
            &caption,
            &human,
            &mut reader,
            part.byte_length,
        )
        .await?;
    let sha256 = reader.finalize();

    let mb = part.byte_length as f64 / (1024.0 * 1024.0);
    tracing::info!(
        idx = part.idx,
        total = total_parts,
        mb,
        elapsed_s = started.elapsed().as_secs_f64(),
        "uploaded part"
    );

    Ok((sent.message_id, sent.doc_id, sha256))
}

fn mime_for(container: &str) -> &'static str {
    match container {
        "mkv" => "video/x-matroska",
        "mp4" => "video/mp4",
        _ => "application/octet-stream",
    }
}

/// Deletes the remux temp file that `add` recorded for this set (meta key
/// `tmp:<set_id>`), then forgets it. Sets without a recorded temp are untouched,
/// so a user's original file can never be removed here.
async fn remove_recorded_tmp(conn: &Connection, set_id: &str) {
    let key = format!("tmp:{set_id}");
    let Ok(Some(path)) = db::get_meta(conn, &key) else {
        return;
    };
    if let Err(err) = tokio::fs::remove_file(&path).await {
        tracing::warn!(path = %path, error = %err, "failed to remove faststart temp file");
    }
    if let Err(err) = db::delete_meta(conn, &key) {
        tracing::warn!(error = %err, "failed to forget faststart temp path");
    }
}
