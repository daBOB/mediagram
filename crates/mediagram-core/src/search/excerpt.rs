//! Showing a person why a summary matched. A port of
//! `web/src/search/excerpt.ts`; see that file for why the window is
//! anchored on a whole word of the original text rather than an offset into
//! a folded copy.

use std::sync::LazyLock;

use regex::Regex;

use super::normalize::variants;

/// Characters of summary either side of a match, counted in `char`s. The
/// web counts UTF-16 code units instead; the two agree for the plain-BMP
/// prose a summary is, and diverging by a character at the very edge of the
/// window is not a difference either surface's viewer could notice.
const EXCERPT_PAD: usize = 90;

static MARKDOWN_MARKS: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"[*_`~]{1,3}").expect("valid regex"));
static LEADING_HEADING: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"(?m)^#{1,6}\s*").expect("valid regex"));
static INLINE_HEADING: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s*#{1,6}\s+").expect("valid regex"));
static WHITESPACE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s+").expect("valid regex"));
static WORD: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"[\p{L}\p{N}]+").expect("valid regex"));

/// Markdown markers out, because an excerpt is a sentence shown to a
/// person. Deliberately crude, the same way the web's `plain` is: the
/// window is cut mid-document, so a real parse would be handed unbalanced
/// markers anyway.
fn plain(text: &str) -> String {
    let text = MARKDOWN_MARKS.replace_all(text, "");
    let text = LEADING_HEADING.replace_all(&text, "");
    let text = INLINE_HEADING.replace_all(&text, " ");
    let text = WHITESPACE.replace_all(&text, " ");
    text.trim().to_string()
}

/// The byte offset of the first word of `summary` whose variants contain a
/// wanted term, or `None`. `variants` is the same function the corpus was
/// matched with, so a hit can never come back with no reason to show for
/// it.
fn anchor(summary: &str, wanted: &[String]) -> Option<usize> {
    WORD.find_iter(summary).find_map(|word| {
        let forms = variants(Some(word.as_str()));
        wanted
            .iter()
            .any(|term| forms.iter().any(|form| form.contains(term.as_str())))
            .then_some(word.start())
    })
}

/// The words around the first matching term, taken from the original text
/// so the reader sees their own language back rather than the folded copy.
pub fn excerpt(summary: Option<&str>, wanted: &[String]) -> Option<String> {
    let summary = summary.filter(|s| !s.is_empty())?;
    let at = anchor(summary, wanted)?;

    // Every char boundary's byte offset, so the window can be padded by
    // character count — not by byte, which would split a multi-byte
    // character in half — without walking the string twice.
    let boundaries: Vec<usize> = summary.char_indices().map(|(byte, _)| byte).collect();
    let at_idx = boundaries.iter().position(|&byte| byte == at).unwrap_or(0);
    let from_idx = at_idx.saturating_sub(EXCERPT_PAD);
    let to_idx = (at_idx + EXCERPT_PAD).min(boundaries.len());

    let from = boundaries[from_idx];
    let to = boundaries.get(to_idx).copied().unwrap_or(summary.len());

    let body = plain(&summary[from..to]);
    Some(format!(
        "{}{body}{}",
        if from > 0 { "…" } else { "" },
        if to < summary.len() { "…" } else { "" }
    ))
}

#[cfg(test)]
#[path = "excerpt_tests.rs"]
mod tests;
