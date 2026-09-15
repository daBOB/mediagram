//! Caption text format:
//!
//! ```text
//! #mlib v=2                      ← marker, exact
//! {"t":"movie",...}              ← minified JSON, plain ASCII
//! 🎬 Dune: Part Two (2024) …     ← optional human lines, free form
//! ```
//!
//! The whole text must fit Telegram's free-tier caption limit (UTF-16 units). The human part
//! is truncated first; the JSON is never touched.

use thiserror::Error;

use crate::caption::Caption;

pub const MARKER: &str = "#mlib v=2";
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
    if version != "2" {
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
mod tests {
    use super::*;

    #[test]
    fn rejects_other_versions_and_garbage() {
        assert!(matches!(
            parse("#mlib v=1\n{}"),
            Err(CaptionError::UnsupportedVersion(_))
        ));
        assert!(matches!(parse("hello"), Err(CaptionError::NoMarker)));
        assert!(matches!(
            parse("#mlib v=2\n"),
            Err(CaptionError::MissingJson)
        ));
        assert!(is_mlib("#mlib v=3\n{}"));
        assert!(!is_mlib("#mlib-index v=2"));
    }
}
