//! Finishes one set: uploads every part still `pending`, adopting parts that
//! already reached the channel from a previous, interrupted run, then marks
//! the set `complete` once nothing is left pending.

use std::collections::HashMap;
use std::path::Path;
use std::time::{Duration, Instant};

use anyhow::{Result, bail};
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;
use rusqlite::Connection;

use super::part_reader::PartReader;
use super::transport::{Seen, Transport};
use crate::index::sets::SetRow;
use crate::index::{parts, sets};

/// An existing channel message already carrying one of our parts, found by
/// scanning recent history instead of re-uploading.
struct Adopted {
    message_id: i64,
    doc_id: i64,
    sha256: String,
}

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
    let template = set_caption_template(set)?;
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
        remove_faststart_tmp(source_path).await;
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

/// The set's `Caption` with a placeholder part block (idx 0, empty hash);
/// used to derive names and human text that don't depend on which part.
fn set_caption_template(set: &SetRow) -> Result<Caption> {
    let t = match set.kind.as_str() {
        "movie" => Kind::Movie,
        "ep" => Kind::Ep,
        other => bail!("set {} has unknown kind `{other}`", set.set_id),
    };
    let e = set
        .episode
        .as_deref()
        .map(serde_json::from_str)
        .transpose()?;
    Ok(Caption {
        t,
        ids: ProviderIds {
            tmdb: set.tmdb,
            tvdb: set.tvdb,
            imdb: set.imdb.clone(),
        },
        show: set.show.clone(),
        title: set.title.clone(),
        year: set.year,
        s: set.season,
        e,
        abs: set.abs,
        q: set.quality.clone(),
        hdr: set.hdr.clone(),
        container: set.container.clone(),
        vcodec: set.vcodec.clone(),
        acodec: set.acodec.clone(),
        alang: set.alang.clone(),
        slang: set.slang.clone(),
        dur: set.duration,
        variant: set.variant.clone(),
        set: set.set_id.clone(),
        part: Part {
            i: 0,
            n: set.part_count,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total: set.total,
    })
}

/// Matches recent channel messages against `set_id` by parsing their
/// caption, keyed by part index so each pending part can be looked up once.
fn adoption_map(set_id: &str, recent: &[Seen]) -> HashMap<u32, Adopted> {
    let mut map = HashMap::new();
    for seen in recent {
        if !mlib_spec::caption_codec::is_mlib(&seen.caption) {
            continue;
        }
        let Ok(caption) = mlib_spec::parse(&seen.caption) else {
            continue;
        };
        if caption.set != set_id {
            continue;
        }
        let Some(doc_id) = seen.doc_id else {
            continue;
        };
        map.entry(caption.part.i).or_insert(Adopted {
            message_id: seen.message_id,
            doc_id,
            sha256: caption.part.sha256,
        });
    }
    map
}

fn mime_for(container: &str) -> &'static str {
    match container {
        "mkv" => "video/x-matroska",
        "mp4" => "video/mp4",
        _ => "application/octet-stream",
    }
}

/// Deletes a remux temp file once its set is done; a no-op for any other path.
async fn remove_faststart_tmp(path: &Path) {
    let is_generated = path
        .file_name()
        .and_then(|n| n.to_str())
        .is_some_and(|n| n.ends_with(".faststart.mp4"));
    if !is_generated {
        return;
    }
    if let Err(err) = tokio::fs::remove_file(path).await {
        tracing::warn!(path = %path.display(), error = %err, "failed to remove faststart temp file");
    }
}
