//! Telegram trims document names to 60 characters. Names here are a human
//! convenience only; captions and the index are authoritative.

use crate::caption::{Caption, Kind, position_code};

pub const MAX_NAME_LEN: usize = 60;
const MAX_EXT_LEN: usize = 8;

/// `Title (Year)`, `Show (Year) - s02e01`, `Show (Year) - s01e01-e02`, or `Show - 1075`.
#[must_use]
pub fn base_name(c: &Caption) -> String {
    let with_year = |name: &str| match c.year {
        Some(y) => format!("{name} ({y})"),
        None => name.to_string(),
    };
    let code =
        c.s.zip(c.e)
            .and_then(|(s, e)| position_code(c.t, s, e))
            .map(|code| code.to_lowercase());
    let raw = match (c.t, c.show.as_deref()) {
        // A standalone documentary reads exactly as a movie does.
        (Kind::Movie, _) | (Kind::Docu, None) => with_year(c.title.as_deref().unwrap_or(&c.set)),
        (Kind::Ep, show) => {
            let show = with_year(show.unwrap_or(&c.set));
            match (code, c.abs) {
                (Some(code), _) => format!("{show} - {code}"),
                (None, Some(a)) => format!("{show} - {a:03}"),
                (None, None) => show,
            }
        }
        // A lesson, a document or a collection's episode: the course, its
        // code, then the item's own title last so truncation eats it first.
        (Kind::Tut | Kind::Doc | Kind::Docu, show) => {
            let course = with_year(show.unwrap_or(&c.set));
            match (code, c.title.as_deref()) {
                (Some(code), Some(title)) => format!("{course} - {code} - {title}"),
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
        Some(i) if hard[..i].chars().count() >= max / 2 => hard[..i].trim_end().to_string(),
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

    /// The word boundary must lie past half of the budget in characters,
    /// not in bytes: a multibyte title would otherwise be cut at its first
    /// word, a fraction of the room it has.
    #[test]
    fn a_multibyte_name_is_not_cut_at_an_early_word() {
        let cjk = format!("{} {}", "語".repeat(9), "語".repeat(50));
        assert_eq!(part_file_name(&cjk, "mkv", 0, 2).chars().count(), 60);
        let umlauts = format!("{} {}", "ä".repeat(20), "ä".repeat(40));
        assert_eq!(part_file_name(&umlauts, "mkv", 0, 2).chars().count(), 60);
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
