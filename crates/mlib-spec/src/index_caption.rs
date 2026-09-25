//! The caption on an index snapshot document (spec §7): a marker line, then
//! one line of JSON saying when the snapshot was pushed and what it holds.
//!
//! ```text
//! #mlib-index v=2
//! {"pushed_at":1700000000,"schema":2,"sets":42}
//! ```
//!
//! Written by the uploader and read by every client, so both sides take the
//! spelling from here rather than each keeping a copy of a wire contract.

use serde::{Deserialize, Serialize};

/// What every index caption starts with, whatever its version. Readers match
/// on this rather than on [`MARKER`], so a future `v=3` snapshot is still
/// recognised as an index.
pub const PREFIX: &str = "#mlib-index";

/// The marker this version of the spec writes.
pub const MARKER: &str = "#mlib-index v=2";

/// The JSON line. Field order is the order written, which is the order the
/// spec documents.
#[derive(Serialize)]
struct Body {
    pushed_at: i64,
    schema: i64,
    sets: i64,
}

/// The caption for a snapshot pushed at `pushed_at` holding `sets` sets.
#[must_use]
pub fn render(pushed_at: i64, sets: i64) -> String {
    let body = Body {
        pushed_at,
        schema: crate::schema::SCHEMA_VERSION,
        sets,
    };
    let json = serde_json::to_string(&body).expect("three integers always serialize");
    format!("{MARKER}\n{json}")
}

/// Whether a message's caption marks it as an index snapshot.
#[must_use]
pub fn is_index(caption: &str) -> bool {
    caption.starts_with(PREFIX)
}

/// When the snapshot was pushed, or `None` when the caption carries no
/// positive timestamp this build can read. Only `pushed_at` is required, so
/// a caption from a later version with more fields still reads.
#[must_use]
pub fn pushed_at(caption: &str) -> Option<i64> {
    #[derive(Deserialize)]
    struct Stamp {
        pushed_at: i64,
    }
    caption
        .split_once('\n')
        .and_then(|(_, json)| serde_json::from_str::<Stamp>(json.trim()).ok())
        .map(|stamp| stamp.pushed_at)
        .filter(|pushed| *pushed > 0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn what_is_written_reads_back() {
        let caption = render(1_781_568_000, 538);
        assert!(is_index(&caption));
        assert!(caption.starts_with(MARKER));
        assert_eq!(pushed_at(&caption), Some(1_781_568_000));
        assert!(caption.ends_with(&format!(
            "{{\"pushed_at\":1781568000,\"schema\":{},\"sets\":538}}",
            crate::schema::SCHEMA_VERSION
        )));
    }

    #[test]
    fn a_part_caption_is_not_an_index() {
        assert!(!is_index("#mlib v=2\n{}"));
    }
}
