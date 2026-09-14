//! Provider identifiers. All optional; a manually tagged title may have none.

use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq, Eq)]
pub struct ProviderIds {
    pub tmdb: Option<u64>,
    pub tvdb: Option<u64>,
    /// IMDb id including the `tt` prefix, e.g. `tt15239678`.
    pub imdb: Option<String>,
}

/// Accepts `tt0816692` or `0816692` (any case); returns the canonical `tt`-prefixed form.
pub fn normalize_imdb(raw: &str) -> Option<String> {
    let raw = raw.trim();
    let digits = raw
        .strip_prefix("tt")
        .or_else(|| raw.strip_prefix("TT"))
        .unwrap_or(raw);
    (!digits.is_empty() && digits.chars().all(|c| c.is_ascii_digit()))
        .then(|| format!("tt{digits}"))
}

impl ProviderIds {
    pub fn is_empty(&self) -> bool {
        self.tmdb.is_none() && self.tvdb.is_none() && self.imdb.is_none()
    }

    /// Build from a `{src}-{id}` token as used in file names, e.g. `tmdb-693134`.
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
        assert!(ProviderIds::from_token("plex", "1").is_none());
        assert!(ProviderIds::default().is_empty());
    }
}
