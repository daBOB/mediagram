//! Provider identifiers. All optional; a manually tagged title may have none.

use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq, Eq)]
pub struct ProviderIds {
    pub tmdb: Option<u64>,
    pub tvdb: Option<u64>,
    /// `IMDb` id including the `tt` prefix, e.g. `tt15239678`.
    pub imdb: Option<String>,
}

/// A provider id as read back from an index column.
///
/// SQLite stores integers as signed 64-bit, so ids go in as `i64`. Reading
/// one back, anything that is not a positive number is not an id a provider
/// could have issued, and is treated as absent rather than cast into a huge
/// or zero one that some reader downstream would have to filter out again.
#[must_use]
pub fn id_from_column(stored: Option<i64>) -> Option<u64> {
    stored
        .and_then(|v| u64::try_from(v).ok())
        .filter(|v| *v > 0)
}

/// Accepts `tt0816692` or `0816692` (any case); returns the canonical `tt`-prefixed form.
#[must_use]
pub fn normalize_imdb(raw: &str) -> Option<String> {
    let raw = raw.trim();
    let digits = match raw.split_at_checked(2) {
        Some((prefix, digits)) if prefix.eq_ignore_ascii_case("tt") => digits,
        _ => raw,
    };
    (!digits.is_empty() && digits.chars().all(|c| c.is_ascii_digit()))
        .then(|| format!("tt{digits}"))
}

impl ProviderIds {
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.tmdb.is_none() && self.tvdb.is_none() && self.imdb.is_none()
    }

    /// Build from a `{src}-{id}` token as used in file names, e.g. `tmdb-693134`.
    #[must_use]
    pub fn from_token(src: &str, id: &str) -> Option<ProviderIds> {
        let mut ids = ProviderIds::default();
        match src {
            "tmdb" => ids.tmdb = Some(id.parse().ok()?),
            "tvdb" => ids.tvdb = Some(id.parse().ok()?),
            "imdb" => ids.imdb = Some(normalize_imdb(id)?),
            _ => return None,
        }
        Some(ids)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn token_parsing() {
        assert_eq!(
            ProviderIds::from_token("tmdb", "42").unwrap().tmdb,
            Some(42)
        );
        assert_eq!(
            ProviderIds::from_token("imdb", "0816692")
                .unwrap()
                .imdb
                .as_deref(),
            Some("tt0816692")
        );
        assert!(ProviderIds::from_token("imdb", "abc").is_none());
        assert_eq!(normalize_imdb(" TT42 ").as_deref(), Some("tt42"));
        assert_eq!(normalize_imdb(" Tt42 ").as_deref(), Some("tt42"));
        assert_eq!(normalize_imdb(" tT42 ").as_deref(), Some("tt42"));
        assert!(normalize_imdb("🎬42").is_none());
        assert!(ProviderIds::from_token("plex", "1").is_none());
        assert!(ProviderIds::default().is_empty());
    }

    #[test]
    fn only_a_positive_stored_id_reads_back_as_one() {
        assert_eq!(id_from_column(Some(603)), Some(603));
        assert_eq!(id_from_column(Some(0)), None);
        assert_eq!(id_from_column(Some(-1)), None);
        assert_eq!(id_from_column(None), None);
    }
}
