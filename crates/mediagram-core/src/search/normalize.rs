//! Folding text so a search finds what a person meant. A line-for-line port
//! of `web/src/search/normalize.ts`; see that file for why both umlaut
//! spellings are kept on the text side, and why the fraction slash and en
//! dash a title can carry collapse to spaces rather than being deleted.

use std::sync::LazyLock;

use regex::Regex;
use unicode_normalization::UnicodeNormalization;

/// Letters with no decomposed form, so NFD cannot strip them the way it does
/// an umlaut — they have to be written out instead. Mirrors `SPELLED_OUT` in
/// the web port.
const SPELLED_OUT: &[(&str, &str)] =
    &[("ß", "ss"), ("æ", "ae"), ("œ", "oe"), ("ø", "o"), ("đ", "d"), ("ð", "d"), ("ł", "l"), ("þ", "th")];

/// The three umlauts, in the two-letter spelling German uses for them.
const UMLAUTS: &[(&str, &str)] = &[("ä", "ae"), ("ö", "oe"), ("ü", "ue")];

/// Text already in the folded alphabet: lower-casing it *is* folding it, so
/// the passes below can be skipped. Mirrors the web's `PLAIN` fast path.
fn is_plain(text: &str) -> bool {
    !text.is_empty() && text.chars().all(|c| c.is_ascii_alphanumeric())
}

// The Unicode `Diacritic` property, not the narrower "is this a combining
// mark" a canonical-combining-class check would give: a standalone acute
// accent (´, U+00B4) and the apostrophe-shaped modifier letter in "ʼn" both
// carry it despite being spacing characters, not marks NFD ever produces —
// dropping only combining marks left them behind as words split by a
// spurious space (`"Geht´s"` folding to `"geht s"` instead of `"gehts"`).
static DIACRITIC: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\p{Diacritic}").expect("valid regex"));
// General-category Letter/Number, not Rust's `char::is_alphanumeric`: that
// method answers the broader derived `Alphabetic` property, which (unlike
// JS's `\p{Letter}`) counts circled and squared compatibility letters like
// "Ⓐ" as letters rather than symbols, so they would survive here instead of
// collapsing to a space the way the web's `[^\p{Letter}\p{Number}]+` does.
static NOT_LETTER_OR_NUMBER: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"[^\p{L}\p{N}]+").expect("valid regex"));

/// Lower case, no diacritics, single-spaced, nothing but letters and
/// digits — the same shape `flatten` in the web port produces. NFD splits a
/// composed umlaut into its base letter and a combining mark, which is what
/// lets the diacritic strip below drop the mark and keep the letter.
fn flatten(text: &str) -> String {
    let decomposed: String = text.nfd().collect();
    let stripped = DIACRITIC.replace_all(&decomposed, "");
    NOT_LETTER_OR_NUMBER.replace_all(&stripped, " ").trim().to_string()
}

/// The folded form of `text`, diacritics dropped: "Überblick" becomes
/// `uberblick`. `None` and `""` both fold to `""`, the same as a falsy
/// string in the web port.
pub fn fold(text: Option<&str>) -> String {
    let Some(text) = text.filter(|t| !t.is_empty()) else { return String::new() };
    if is_plain(text) {
        return text.to_lowercase();
    }
    let mut folded = text.to_lowercase();
    for (pattern, replacement) in SPELLED_OUT {
        folded = folded.replace(pattern, replacement);
    }
    flatten(&folded)
}

/// The folded form of `text` with umlauts spelled out instead of dropped:
/// "Überblick" becomes `ueberblick`. Composed to NFC first, so an umlaut
/// arriving already decomposed is not stripped by `fold`'s `flatten` before
/// this ever sees it as one character.
pub fn spell_out(text: Option<&str>) -> String {
    let Some(text) = text.filter(|t| !t.is_empty()) else { return String::new() };
    if is_plain(text) {
        return text.to_lowercase();
    }
    let mut spelled = text.to_lowercase().nfc().collect::<String>();
    for (pattern, replacement) in UMLAUTS {
        spelled = spelled.replace(pattern, replacement);
    }
    // Folded rather than flattened, so the two functions cannot drift: this
    // is where `fold`'s spelled-out sibling gets everything `fold` does
    // beyond dropping diacritics.
    fold(Some(&spelled))
}

/// The forms `text` can be searched as: one string, or two when spelling
/// the umlauts out reads differently from dropping them.
pub fn variants(text: Option<&str>) -> Vec<String> {
    let folded = fold(text);
    let spelled = spell_out(text);
    if spelled == folded { vec![folded] } else { vec![folded, spelled] }
}

/// The folded words of a query. An empty query yields no terms.
pub fn terms(query: Option<&str>) -> Vec<String> {
    let folded = fold(query);
    if folded.is_empty() { Vec::new() } else { folded.split(' ').map(str::to_string).collect() }
}

#[cfg(test)]
#[path = "normalize_tests.rs"]
mod tests;
