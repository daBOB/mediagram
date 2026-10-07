//! The caption on an index snapshot document (spec §7): a marker line, then
//! one line of JSON saying when the snapshot was pushed and what it holds.
//!
//! ```text
//! #mlib-index v=2
//! {"pushed_at":1700000000,"schema":2,"sets":42,"uploader":"0.83.1"}
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
    /// This crate's own version, workspace-inherited so it always equals the
    /// uploader binary's — checkable from the *other* machine's own pin
    /// without either one running a command, just reading a caption.
    uploader: &'static str,
}

/// The caption for a snapshot pushed at `pushed_at` holding `sets` sets.
///
/// # Panics
/// Never, in practice: the body is only strings and integers, which always
/// serialize.
#[must_use]
pub fn render(pushed_at: i64, sets: i64) -> String {
    let body = Body {
        pushed_at,
        schema: crate::schema::SCHEMA_VERSION,
        sets,
        uploader: env!("CARGO_PKG_VERSION"),
    };
    let json = serde_json::to_string(&body).expect("three integers and a version always serialize");
    format!("{MARKER}\n{json}")
}

/// The `library.db` schema a caption records, or `None` when it carries none
/// this build can parse. Present on every caption alongside `pushed_at`, so
/// a publisher can be refused a channel it cannot yet read without ever
/// downloading the snapshot itself.
#[must_use]
pub fn schema(caption: &str) -> Option<i64> {
    #[derive(Deserialize)]
    struct Stamp {
        schema: i64,
    }
    caption
        .split_once('\n')
        .and_then(|(_, json)| serde_json::from_str::<Stamp>(json.trim()).ok())
        .map(|stamp| stamp.schema)
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

/// How far ahead of a reader's clock a timestamp may be and still be
/// believed. Clocks disagree by minutes, not days; a snapshot dated next year
/// is either a mistake or an attempt to make every later one look stale.
pub const FUTURE_TOLERANCE_SECONDS: i64 = 24 * 60 * 60;

/// The `pushed_at` a reader at `now` believes: `None` when the caption
/// carries none, or one further ahead than [`FUTURE_TOLERANCE_SECONDS`].
#[must_use]
pub fn believed_pushed_at(caption: &str, now: i64) -> Option<i64> {
    pushed_at(caption).filter(|pushed| *pushed <= now + FUTURE_TOLERANCE_SECONDS)
}

/// Which of `candidates` — `(caption, message id)` — is the channel's index
/// (spec §7), or `None` when none of them is an index at all.
///
/// Newest by believed `pushed_at`, not by whichever one a pin points at: a
/// pin can be left behind by a publish whose unpin failed, while the
/// timestamp travels with the snapshot. A caption whose time cannot be
/// believed loses to any that can, and the higher message id breaks a tie,
/// so every reader makes the same choice.
///
/// Callers keep only the channel's own posts before asking: a message a
/// member slipped in is not a snapshot anyone published, and telling the two
/// apart needs the message, not its caption.
#[must_use]
pub fn newest(candidates: &[(&str, i64)], now: i64) -> Option<usize> {
    candidates
        .iter()
        .enumerate()
        .filter(|(_, (text, _))| is_index(text))
        .max_by_key(|(_, (text, id))| (believed_pushed_at(text, now), *id))
        .map(|(index, _)| index)
}

#[cfg(test)]
#[path = "index_caption_tests.rs"]
mod tests;
