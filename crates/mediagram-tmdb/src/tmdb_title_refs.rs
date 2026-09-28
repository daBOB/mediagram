//! Small shapes `DetailsResponse` nests one level down: a film's franchise,
//! a series' creators, and a series' seasons. Split out of `tmdb_types.rs` so
//! that file stays under the line limit `code_standards.rs` holds every
//! source file to; re-exported from there so no caller's `use` path changes.

use serde::Deserialize;

/// The franchise (TMDB "collection") a film's `belongs_to_collection` names.
///
/// `name` is optional though TMDB always sends it: these ride on the details
/// payload, and a required field there that one record left null would fail
/// the whole parse — costing the title its description and its artwork for
/// the sake of a franchise name.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct CollectionRef {
    pub id: u64,
    #[serde(default)]
    pub name: Option<String>,
}

/// One entry of a series' `created_by`. `name` is optional for the reason
/// [`CollectionRef`]'s is; a creator without one is skipped.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct CreatedBy {
    pub id: u64,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub profile_path: Option<String>,
}

/// One entry of a series' `seasons`: its number and its artwork, if any.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct SeasonRef {
    pub season_number: u32,
    #[serde(default)]
    pub poster_path: Option<String>,
}
