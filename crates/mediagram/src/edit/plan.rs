//! What a correction changes, and what it must not.
//!
//! Metadata is a guess that can be wrong — a title in the wrong language, a
//! show matched to the wrong id — and the channel is the archive, so
//! correcting it means rewriting captions rather than re-uploading bytes.
//!
//! What is never editable is anything describing those bytes: the set id, the
//! part geometry, the hashes and the total. Those are what `verify` checks
//! and what a player seeks with, and an edit that touched them would turn a
//! correction into corruption.

use anyhow::{Context, Result};

use crate::index::parts::PartRow;
use crate::index::sets::SetRow;

/// Fields a person may correct. `None` leaves a field as it was.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Edits {
    pub title: Option<String>,
    pub show: Option<String>,
    pub year: Option<u16>,
    pub season: Option<u32>,
    pub episode: Option<u32>,
    pub chap: Option<String>,
    pub path: Option<String>,
}

impl Edits {
    /// True when nothing was asked for, so callers can refuse a no-op rather
    /// than rewrite a channel's worth of identical captions.
    pub fn is_empty(&self) -> bool {
        *self == Edits::default()
    }
}

/// One message to rewrite.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CaptionWrite {
    pub message_id: i64,
    pub part_idx: u32,
    pub text: String,
}

/// Applies `edits` to a row, leaving everything they do not mention.
pub fn apply(row: &SetRow, edits: &Edits) -> SetRow {
    let mut edited = row.clone();
    if let Some(title) = &edits.title {
        edited.title = Some(title.clone());
    }
    if let Some(show) = &edits.show {
        edited.show = Some(show.clone());
    }
    if let Some(year) = edits.year {
        edited.year = Some(year);
    }
    if let Some(season) = edits.season {
        edited.season = Some(season);
    }
    if let Some(episode) = edits.episode {
        edited.episode = Some(episode.to_string());
    }
    if let Some(chap) = &edits.chap {
        edited.chap = Some(chap.clone());
    }
    if let Some(path) = &edits.path {
        edited.path = Some(path.clone());
    }
    edited
}

/// The caption each uploaded part should now carry.
///
/// Built from each part's own row, never by copying one part's record onto
/// another: a caption describes the bytes in its own message, and `verify`
/// compares them. A part with no message was never uploaded and is simply not
/// part of this job.
///
/// Every caption is rendered before any is sent, so an edit that overflows
/// the caption budget fails with nothing written rather than halfway through.
pub fn captions(row: &SetRow, parts: &[PartRow]) -> Result<Vec<CaptionWrite>> {
    let template = row.caption_template()?;
    let mut writes = Vec::new();

    for part in parts {
        let Some(message_id) = part.message_id else {
            continue;
        };
        let caption = template.with_part(mlib_spec::caption::Part {
            i: part.idx,
            n: row.part_count,
            off: part.byte_offset,
            len: part.byte_length,
            sha256: part.sha256.clone().unwrap_or_default(),
        });
        let text = mlib_spec::to_text(&caption, "").with_context(|| {
            format!(
                "rendering the new caption for part {} of {}",
                part.idx, row.set_id
            )
        })?;
        writes.push(CaptionWrite {
            message_id,
            part_idx: part.idx,
            text,
        });
    }
    Ok(writes)
}
