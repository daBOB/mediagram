//! Rewriting a conflicting set's row from its own captions.
//!
//! `merge::merge_from` finds shared sets whose metadata differs but picks no
//! side: a caption is the one place a set's own upload wrote that metadata,
//! and neither index is more authoritative than the other about it. This
//! resolves the conflict the way `edit` already does — by rewriting the row
//! from a caption, via the same [`crate::index::set_row::SetRow::from_caption`]
//! mapping `rescan` uses to rebuild a set from scratch.

use anyhow::{Context, Result, bail};
use rusqlite::{Connection, OptionalExtension};

use crate::index::set_row::SetRow;
use crate::index::sets;

/// What trying to resolve one conflicting set from its captions found.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct ConflictOutcome {
    /// The row was rewritten from a caption.
    pub applied: bool,
    /// The set's own `kind`, read straight from the column rather than
    /// decoded — not one this build knows, so the row was left exactly as
    /// it was rather than risking a decode failure over it.
    pub unknown_kind: Option<String>,
    /// Candidate captions skipped because their `#mlib v=N` is newer than
    /// this build reads — not necessarily this set's own, since a caption
    /// that unreadable names no set this build can check.
    pub newer_captions: usize,
}

/// Rewrites `set_id`'s metadata from the first caption in `captions` that
/// parses as one of its own parts. `captions` is raw message text, gone
/// entries included as empty strings — this only reads what parses.
///
/// Pure: a test supplies caption text directly instead of a Telegram fetch.
pub fn resolve_from_captions(
    conn: &Connection,
    set_id: &str,
    captions: &[String],
) -> Result<ConflictOutcome> {
    let kind: Option<String> = conn
        .query_row("SELECT kind FROM sets WHERE set_id = ?1", [set_id], |row| {
            row.get(0)
        })
        .optional()?;
    let Some(kind) = kind else {
        bail!("set {set_id} vanished before its conflict could be resolved");
    };
    if kind.parse::<mlib_spec::Kind>().is_err() {
        return Ok(ConflictOutcome {
            unknown_kind: Some(kind),
            ..ConflictOutcome::default()
        });
    }
    let existing = sets::get_set(conn, set_id)?
        .with_context(|| format!("set {set_id} vanished before its conflict could be resolved"))?;

    let mut newer_captions = 0usize;
    for text in captions {
        if !mlib_spec::caption_codec::is_mlib(text) {
            continue;
        }
        let caption = match mlib_spec::parse(text) {
            Ok(caption) => caption,
            Err(mlib_spec::CaptionError::UnsupportedVersion(_)) => {
                newer_captions += 1;
                continue;
            }
            Err(_) => continue,
        };
        if caption.set != set_id {
            continue;
        }
        let row = SetRow::from_caption(&caption, existing.created_at);
        sets::update_metadata(conn, &row)?;
        return Ok(ConflictOutcome {
            applied: true,
            newer_captions,
            ..ConflictOutcome::default()
        });
    }
    Ok(ConflictOutcome {
        newer_captions,
        ..ConflictOutcome::default()
    })
}

#[cfg(test)]
#[path = "merge_conflicts_tests.rs"]
mod tests;
