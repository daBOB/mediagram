//! Slugs for collection ids.
//!
//! Lives in the spec crate because the package format documents a collection
//! id's default derivation: any client generating one should produce the same
//! string from the same title.

/// Lowercase ASCII alphanumerics, runs of anything else collapsed to a single
/// dash, trimmed. Non-ASCII characters are dropped rather than transliterated,
/// so a title of only non-ASCII yields an empty slug and the caller must ask
/// for an explicit id instead of inventing one.
#[must_use]
pub fn slug(title: &str) -> String {
    let mut out = String::with_capacity(title.len());
    let mut pending_dash = false;
    for ch in title.chars() {
        if ch.is_ascii_alphanumeric() {
            if pending_dash && !out.is_empty() {
                out.push('-');
            }
            pending_dash = false;
            out.push(ch.to_ascii_lowercase());
        } else {
            pending_dash = true;
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::slug;

    #[test]
    fn a_title_becomes_a_readable_slug() {
        assert_eq!(slug("Rust Course 2024"), "rust-course-2024");
    }

    #[test]
    fn punctuation_collapses_rather_than_doubling_dashes() {
        assert_eq!(slug("Rust -- The  Book!!"), "rust-the-book");
        assert_eq!(slug("  leading and trailing  "), "leading-and-trailing");
    }

    #[test]
    fn a_slug_is_safe_to_put_in_a_path() {
        for hostile in ["../../etc/passwd", "a/b\\c", "..", "with\nnewline"] {
            let s = slug(hostile);
            assert!(
                !s.contains('/') && !s.contains('\\') && !s.contains(".."),
                "{s}"
            );
        }
    }

    #[test]
    fn a_title_with_no_ascii_yields_an_empty_slug() {
        assert_eq!(slug("日本語"), "");
    }
}
