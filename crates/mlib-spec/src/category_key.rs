//! The per-unit key a category is filed under: a course, a documentary
//! collection, or a standalone documentary — exactly the unit each
//! department page draws as one card, and the same unit whose custom
//! artwork already lives under [`crate::package::title_art_key`].
//!
//! `kind` is a bare `&str` rather than [`crate::Kind`] so the uploader
//! (`row.kind.as_str()`), the Android core (a string kind read off a caption
//! row) and the web player's TypeScript port share one signature and can all
//! run the same fixture (`web/test/fixtures/categories/keys.json`,
//! checked here by `crates/mlib-spec/tests/shared_category_keys.rs`).

use crate::package::title_art_key;

/// Department for a course or one of its documents.
pub const TUTORIALS: &str = "tutorials";
/// Department for a documentary collection or a standalone documentary.
pub const DOCUMENTARIES: &str = "documentaries";

/// The `(department, item_key)` a category on this unit is filed under, or
/// `None` when `kind` names no unit at all (a film or an episode, or a
/// spelling no version of the spec defines) or `show`/`title` slugs to
/// nothing (a name of only punctuation, or only a script the slug drops).
///
/// `show.or(title)` is the same choice a unit's custom artwork already
/// makes: a course or a documentary collection is named by its `show`, a
/// standalone documentary by its `title`.
#[must_use]
pub fn category_key(kind: &str, show: Option<&str>, title: Option<&str>) -> Option<(&'static str, String)> {
    let department = match kind {
        "tut" | "doc" => TUTORIALS,
        "docu" => DOCUMENTARIES,
        _ => return None,
    };
    let item_key = title_art_key(show.or(title)?)?;
    Some((department, item_key))
}
