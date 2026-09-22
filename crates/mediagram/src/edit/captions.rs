//! The captions an edit rewrites: one per uploaded part, each rendered from
//! that part's own row.

use anyhow::{Context, Result};

use crate::index::parts::PartRow;
use crate::index::set_row::SetRow;

/// One message to rewrite.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CaptionWrite {
    pub message_id: i64,
    pub part_idx: u32,
    pub text: String,
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
