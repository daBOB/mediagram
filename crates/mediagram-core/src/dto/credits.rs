//! The records `Core::title_credits`/`person`/`franchises`/`search_people`
//! hand to Kotlin — the departments the web player's `catalog/credits.ts`
//! reads, flattened for the binding surface the way `TitleInfo` is.

/// One person credited on a title, or found by a name search: their id,
/// name, the character they played (cast) or their job (crew), and where
/// their portrait is held — present only when this device already holds the
/// file, the same rule `SetSummary::backdrop_path` is held to. Resolved here
/// rather than left as a key: a title's whole cast crosses the boundary in
/// one call, and a caller resolving each member's key back into a path
/// would turn that one crossing into one per name.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CreditRecord {
    pub person_id: u64,
    pub name: String,
    pub role: Option<String>,
    pub portrait_path: Option<String>,
}

/// A title's cast, in billing order, apart from its crew (director(s), a
/// series' creators).
#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct TitleCreditsRecord {
    pub cast: Vec<CreditRecord>,
    pub crew: Vec<CreditRecord>,
}

/// One person and the keys of every title they are credited on — a caller
/// resolves these against the rows it was already allowed to see, so a Kids
/// profile is shown only the titles it can already open.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PersonRecord {
    pub person_id: u64,
    pub name: String,
    pub portrait_path: Option<String>,
    pub title_keys: Vec<String>,
}

/// A film franchise (TMDB "collection"), by name.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct FranchiseRecord {
    pub id: u64,
    pub name: String,
    pub overview: Option<String>,
}

/// One name [`crate::api::Core::search_people`] found, most-credited people
/// surfacing first.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PeopleHitRecord {
    pub person_id: u64,
    pub name: String,
    pub portrait_path: Option<String>,
    pub title_keys: Vec<String>,
}
