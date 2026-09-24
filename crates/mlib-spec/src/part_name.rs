//! Telegram trims document names to 60 characters. Names here are a human
//! convenience only; captions and the index are authoritative.

use crate::caption::{Caption, Episode, Kind};

pub const MAX_NAME_LEN: usize = 60;
const MAX_EXT_LEN: usize = 8;

/// `Title (Year)`, `Show (Year) - s02e01`, `Show (Year) - s01e01-e02`, or `Show - 1075`.
#[must_use]
pub fn base_name(c: &Caption) -> String {
    let with_year = |name: &str| match c.year {
        Some(y) => format!("{name} ({y})"),
        None => name.to_string(),
    };
    let raw = match c.t {
        Kind::Movie => with_year(c.title.as_deref().unwrap_or(&c.set)),
        Kind::Ep => {
            let show = with_year(c.show.as_deref().unwrap_or(&c.set));
            match (c.s, c.e, c.abs) {
                (Some(s), Some(e), _) => format!("{show} - {}", lower_code(s, e)),
                (_, _, Some(a)) => format!("{show} - {a:03}"),
                _ => show,
            }
        }
        Kind::Tut | Kind::Doc => {
            let course = with_year(c.show.as_deref().unwrap_or(&c.set));
            let code = match (c.s, c.e) {
                (Some(ch), Some(n)) => Some(
                    match c.t {
                        Kind::Doc => crate::caption::document_code(ch, n),
                        _ => crate::caption::lesson_code(ch, n),
                    }
                    .to_lowercase(),
                ),
                _ => None,
            };
            match (code, c.title.as_deref()) {
                // The lesson title is last so truncation eats it first.
                (Some(code), Some(lesson)) => format!("{course} - {code} - {lesson}"),
                (Some(code), None) => format!("{course} - {code}"),
                (None, _) => course,
            }
        }
    };
    sanitize(&raw)
}

/// `<base>.<ext>` for single-part sets, `<base>.<ext>.pNNN` otherwise; ≤60 chars.
#[must_use]
pub fn part_file_name(base: &str, ext: &str, idx: u32, n: u32) -> String {
    // Real container extensions are short; cap so the suffix can never eat the budget.
    let ext: String = ext.chars().take(MAX_EXT_LEN).collect();
    let suffix = if n > 1 {
        format!(".{ext}.p{idx:03}")
    } else {
        format!(".{ext}")
    };
    let room = MAX_NAME_LEN.saturating_sub(suffix.chars().count());
    format!("{}{suffix}", truncate_words(&sanitize(base), room))
}

fn lower_code(season: u32, e: Episode) -> String {
    match e {
        Episode::Single(n) => format!("s{season:02}e{n:02}"),
        Episode::Range([a, b]) => format!("s{season:02}e{a:02}-e{b:02}"),
    }
}

/// Drop characters that are illegal in common file systems, collapse whitespace.
fn sanitize(s: &str) -> String {
    let cleaned: String = s
        .chars()
        .map(|c| {
            if matches!(c, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|') {
                ' '
            } else {
                c
            }
        })
        .filter(|c| !c.is_control())
        .collect();
    cleaned.split_whitespace().collect::<Vec<_>>().join(" ")
}

/// Truncate to `max` chars, preferring a word boundary when one exists past half.
fn truncate_words(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        return s.to_string();
    }
    let hard: String = s.chars().take(max).collect();
    match hard.rfind(' ') {
        Some(i) if i >= max / 2 => hard[..i].trim_end().to_string(),
        _ => hard.trim_end().to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn names_fit_and_keep_suffix() {
        let long = "A Very Long Documentary Title About Absolutely Everything Ever Made (2019)";
        let name = part_file_name(long, "mkv", 3, 18);
        assert!(name.chars().count() <= MAX_NAME_LEN, "{name}");
        assert!(name.ends_with(".mkv.p003"));
        assert_eq!(
            part_file_name("Dune Part Two (2024)", "mkv", 0, 1),
            "Dune Part Two (2024).mkv"
        );
    }

    #[test]
    fn absurd_extension_still_fits() {
        assert!(
            part_file_name("Title", &"a".repeat(80), 0, 1)
                .chars()
                .count()
                <= MAX_NAME_LEN
        );
    }

    #[test]
    fn sanitize_strips_illegal_chars() {
        assert_eq!(
            sanitize("Dune: Part Two / Reloaded?"),
            "Dune Part Two Reloaded"
        );
    }
}
