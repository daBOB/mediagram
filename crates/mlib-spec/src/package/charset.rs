//! The character classes the package format's names are checked against.

pub(super) fn is_lower_alpha(s: &str) -> bool {
    !s.is_empty() && s.chars().all(|c| c.is_ascii_lowercase())
}

pub(super) fn is_digits(s: &str) -> bool {
    !s.is_empty() && s.chars().all(|c| c.is_ascii_digit())
}

/// Lowercase hex of an exact length. Used on every pointer field that reaches
/// the cipher or a file name: it keeps `key_id` free of any character a JSON
/// writer might escape differently, which is what makes the associated data
/// reproducible by a reader using a different JSON library.
pub(super) fn is_lower_hex(s: &str, len: usize) -> bool {
    s.len() == len
        && s.bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}
