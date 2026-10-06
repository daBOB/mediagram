//! Re-reading a merge's conflicting sets from their captions: the resolution
//! `index::merge` defers rather than picking a side for.

use anyhow::Result;
use rusqlite::Connection;

use super::remote::ChannelRemote;
use crate::index::merge_conflicts::resolve_from_captions;
use crate::index::parts;
use crate::index::sets_pending::SkippedKind;

/// What resolving every conflicting set found.
#[derive(Debug, Default, Clone)]
pub(super) struct ResolveSummary {
    pub resolved: usize,
    /// Every `kind` a conflicting set carried that this build cannot decode,
    /// and how many sets — from a newer uploader, left as they were.
    pub skipped_kinds: Vec<SkippedKind>,
    /// Candidate captions this build could not read because of a newer
    /// `#mlib v=N`, summed across every conflicting set.
    pub newer_captions: usize,
}

/// Re-fetches every conflicting set's part messages and rewrites its row
/// from whichever caption parses as its own. A set whose messages are all
/// gone, whose captions no longer parse, or whose own `kind` this build
/// cannot decode, is left as the merge found it and is not counted resolved.
pub(super) async fn resolve(
    conn: &Connection,
    remote: &impl ChannelRemote,
    conflicts: &[String],
) -> Result<ResolveSummary> {
    let mut summary = ResolveSummary::default();
    for set_id in conflicts {
        let ids: Vec<i32> = parts::all_parts(conn, set_id)?
            .into_iter()
            .filter_map(|p| p.message_id)
            .filter_map(|id| i32::try_from(id).ok())
            .collect();
        if ids.is_empty() {
            continue;
        }
        let found = match remote.captions(&ids).await {
            Ok(found) => found,
            // One set's captions out of reach leaves that set as it was;
            // the next pull tries it again.
            Err(err) => {
                tracing::warn!(set_id, error = %format_args!("{err:#}"), "could not re-read captions; left as it was");
                continue;
            }
        };
        // In part order: an interrupted edit rewrites captions from the first
        // part on, so the lowest part carries the newest metadata.
        let captions: Vec<String> = ids.iter().filter_map(|id| found.get(id).cloned()).collect();
        let outcome = resolve_from_captions(conn, set_id, &captions)?;
        summary.newer_captions += outcome.newer_captions;
        if outcome.applied {
            summary.resolved += 1;
        }
        if let Some(kind) = outcome.unknown_kind {
            match summary.skipped_kinds.iter_mut().find(|s| s.kind == kind) {
                Some(existing) => existing.count += 1,
                None => summary.skipped_kinds.push(SkippedKind { kind, count: 1 }),
            }
        }
    }
    Ok(summary)
}

#[cfg(test)]
#[path = "conflicts_tests.rs"]
mod tests;
