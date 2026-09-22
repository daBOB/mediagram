//! Caption text format:
//!
//! ```text
//! #mlib v=4                      ← marker, exact
//! {"t":"movie",...}              ← minified JSON, plain ASCII
//! 🎬 Dune: Part Two (2024) …     ← optional human lines, free form
//! ```
//!
//! The whole text must fit Telegram's free-tier caption limit (UTF-16 units). The human part
//! is truncated first; the JSON is never touched.

use thiserror::Error;

use crate::caption::Caption;

/// Marker written on every new caption. Readers accept older versions too;
/// see [`parse`].
pub const MARKER: &str = "#mlib v=4";
pub const MARKER_PREFIX: &str = "#mlib v=";
/// Free-tier caption limit, counted in UTF-16 code units like Telegram does;
/// Premium-independent by design.
pub const CAPTION_BUDGET: usize = 1024;

/// Length as Telegram measures it.
pub fn tg_len(s: &str) -> usize {
    s.encode_utf16().count()
}

#[derive(Error, Debug)]
pub enum CaptionError {
    #[error("marker + json is {len} chars, over the {CAPTION_BUDGET} budget")]
    BudgetExceeded { len: usize },
    #[error("caption does not start with `{MARKER_PREFIX}`")]
    NoMarker,
    #[error("unsupported caption version `{0}`")]
    UnsupportedVersion(String),
    #[error("caption has no JSON line")]
    MissingJson,
    #[error("caption JSON: {0}")]
    Json(#[from] serde_json::Error),
    #[error("caption field `{0}` is not usable")]
    InvalidField(&'static str),
}

/// Longest a set id may be. Real ones are 26-character ULIDs; the limit only
/// exists so a hostile caption cannot make one unbounded.
const MAX_SET_LEN: usize = 64;

/// Bounds on `path`. Generous for any real course, small enough that a
/// hostile caption cannot make a reader allocate or recurse without limit.
const MAX_PATH_LEN: usize = 512;
const MAX_PATH_DEPTH: usize = 16;

/// True if `path` is a collection-relative label and nothing more.
///
/// Refuses what a careless reader could turn into a filesystem escape: `..`,
/// absolute paths, empty or dot segments, and backslashes, which some
/// platforms treat as separators too.
fn path_ok(path: &str) -> bool {
    if path.is_empty() || path.len() > MAX_PATH_LEN {
        return false;
    }
    if path.starts_with('/') || path.ends_with('/') || path.contains('\\') {
        return false;
    }
    let segments: Vec<&str> = path.split('/').collect();
    segments.len() <= MAX_PATH_DEPTH
        && segments
            .iter()
            .all(|segment| !segment.is_empty() && *segment != "." && *segment != "..")
}

/// Captions arrive from a channel anyone with access can post to, and
/// `rescan` writes these values into the index, where `set` becomes a primary
/// key and, for the package export, part of a file name. Structural
/// validation therefore belongs here, at the boundary, not at each use.
fn validate(caption: &Caption) -> Result<(), CaptionError> {
    let set_ok = !caption.set.is_empty()
        && caption.set.len() <= MAX_SET_LEN
        && caption.set.chars().all(|c| c.is_ascii_alphanumeric());
    if !set_ok {
        return Err(CaptionError::InvalidField("set"));
    }
    if let Some(path) = &caption.path
        && !path_ok(path)
    {
        return Err(CaptionError::InvalidField("path"));
    }
    if caption.part.n == 0 || caption.part.i >= caption.part.n {
        return Err(CaptionError::InvalidField("part"));
    }
    // The index stores these as signed 64-bit integers.
    let fits = |v: u64| i64::try_from(v).is_ok();
    if !fits(caption.part.len) || !fits(caption.part.off) || !fits(caption.total) {
        return Err(CaptionError::InvalidField("length"));
    }
    // `sha256` is deliberately not validated here. It never reaches a path or
    // a key; it is only ever compared. A malformed one fails `verify`, which
    // is the right place to notice, whereas refusing the caption would make
    // `rescan` drop a recoverable part during disaster recovery.
    Ok(())
}

/// Render a caption. `human` may be empty; it is truncated to fit the budget.
/// Whether every part of the set `template` describes fits the budget.
///
/// The longest caption a set produces is its last part's: the largest offset
/// and length, and a full hash. Measured on the template before anything is
/// written, because a caption that cannot be sent is a set that cannot be
/// finished.
pub fn check_budget(template: &Caption) -> Result<(), CaptionError> {
    let n = template.part.n;
    let last = template.with_part(crate::caption::Part {
        i: n.saturating_sub(1),
        n,
        off: template.total,
        len: template.total,
        sha256: "0".repeat(64),
    });
    to_text(&last, &last.display_name()).map(drop)
}

pub fn to_text(c: &Caption, human: &str) -> Result<String, CaptionError> {
    let json = serde_json::to_string(c)?;
    let mut out = format!("{MARKER}\n{json}");
    let used = tg_len(&out);
    if used > CAPTION_BUDGET {
        return Err(CaptionError::BudgetExceeded { len: used });
    }
    let human = human.trim();
    if !human.is_empty() {
        let room = CAPTION_BUDGET.saturating_sub(used + 1);
        if room > 0 {
            out.push('\n');
            out.extend(take_utf16(human, room));
        }
    }
    Ok(out)
}

/// Longest prefix of `s` that fits in `units` UTF-16 code units, never splitting a char.
fn take_utf16(s: &str, units: usize) -> impl Iterator<Item = char> + '_ {
    let mut used = 0;
    s.chars().take_while(move |c| {
        used += c.len_utf16();
        used <= units
    })
}

/// True if the text looks like an mlib caption of any version.
pub fn is_mlib(text: &str) -> bool {
    text.trim_start().starts_with(MARKER_PREFIX)
}

/// Parse a caption. Tolerates CRLF and trailing human lines.
pub fn parse(text: &str) -> Result<Caption, CaptionError> {
    let mut lines = text.trim_start().lines().map(|l| l.trim_end_matches('\r'));
    let marker = lines.next().ok_or(CaptionError::NoMarker)?.trim();
    let version = marker
        .strip_prefix(MARKER_PREFIX)
        .ok_or(CaptionError::NoMarker)?;
    // Accept every version this build can decode, not just the newest: a
    // channel holds a mix from before and after an uploader upgrade, and both
    // must stay readable. v2 captions lack `cid` and `chap`, v3 lacks `path`.
    if !matches!(version, "2" | "3" | "4") {
        return Err(CaptionError::UnsupportedVersion(version.to_string()));
    }
    let json = lines.next().ok_or(CaptionError::MissingJson)?.trim();
    if json.is_empty() {
        return Err(CaptionError::MissingJson);
    }
    let caption: Caption = serde_json::from_str(json)?;
    validate(&caption)?;
    Ok(caption)
}

#[cfg(test)]
mod version_agreement {
    /// The marker line and `SPEC_VERSION` are the same number said twice.
    /// They drifted apart once, when `path` was added and only the marker
    /// moved, which put the wrong version in every published package.
    #[test]
    fn the_marker_names_the_spec_version() {
        assert_eq!(super::MARKER, format!("#mlib v={}", crate::SPEC_VERSION));
    }
}

#[cfg(test)]
#[path = "caption_codec_tests.rs"]
mod tests;
