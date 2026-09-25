//! Re-reading a merge's conflicting sets from their captions: the resolution
//! `index::merge` defers rather than picking a side for.

use anyhow::Result;
use rusqlite::Connection;

use crate::config::Config;
use crate::index::merge_conflicts::resolve_from_captions;
use crate::index::parts;
use crate::telegram::client::Tg;
use crate::verify::download_hash::fetch_messages;

/// Re-fetches every conflicting set's part messages and rewrites its row
/// from whichever caption parses as its own. Returns how many were resolved;
/// a set whose messages are all gone, or whose captions no longer parse, is
/// left as the merge found it and is not counted.
pub(super) async fn resolve(
    conn: &Connection,
    tg: &Tg,
    cfg: &Config,
    conflicts: &[String],
) -> Result<usize> {
    let mut resolved = 0usize;
    for set_id in conflicts {
        let ids: Vec<i32> = parts::all_parts(conn, set_id)?
            .into_iter()
            .filter_map(|p| p.message_id)
            .filter_map(|id| i32::try_from(id).ok())
            .collect();
        if ids.is_empty() {
            continue;
        }
        let found = match fetch_messages(&tg.client, tg.channel, &ids, cfg.max_attempts).await {
            Ok(found) => found,
            // One set's captions out of reach leaves that set as it was;
            // the next pull tries it again.
            Err(err) => {
                tracing::warn!(set_id, error = %err, "could not re-read captions; left as it was");
                continue;
            }
        };
        // In part order: an interrupted edit rewrites captions from the first
        // part on, so the lowest part carries the newest metadata.
        let captions: Vec<String> = ids
            .iter()
            .filter_map(|id| found.get(id))
            .map(|m| m.text().to_string())
            .collect();
        if resolve_from_captions(conn, set_id, &captions)? {
            resolved += 1;
        }
    }
    Ok(resolved)
}
