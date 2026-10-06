//! What the local index holds for an item, found by who the item is.
//!
//! Who an item is comes from the data it is planned with — the same data the
//! set is recorded under — so the lookup and the record cannot disagree.
//! When they were stated apart, a documentary collection was recorded as
//! `docu` and looked up as `tut`, and every re-run uploaded it again.

use anyhow::Result;
use rusqlite::Connection;

use super::Set;
use crate::index::status::SetStatus;
use crate::index::{set_lookup, sets};
use crate::upload::new_set::{LessonOf, NewSet};

pub(super) fn status(conn: &Connection, set: &Set) -> Result<Option<SetStatus>> {
    match set {
        Set::Planned(id) => Ok(sets::get_set(conn, id)?.map(|row| row.status)),
        Set::Document(doc) => set_lookup::document_status(conn, &doc.cid, doc.chapter, doc.number),
        Set::File(new) => file_status(conn, new),
    }
}

/// A lesson or a collection's episode by its collection and numbers, an
/// episode by its show and numbers; anything else has no identity to find,
/// so it is always planned anew.
fn file_status(conn: &Connection, new: &NewSet) -> Result<Option<SetStatus>> {
    match (&new.lesson, new.tmdb, new.season, new.episode) {
        (
            Some(LessonOf {
                cid,
                chapter: Some(chapter),
                number: Some(number),
                kind,
                ..
            }),
            ..,
        ) => set_lookup::lesson_status(conn, cid, *chapter, *number, *kind),
        (None, Some(tmdb), Some(season), Some(episode)) => {
            set_lookup::episode_status(conn, tmdb, season, episode)
        }
        _ => Ok(None),
    }
}

#[cfg(test)]
#[path = "identity_tests.rs"]
mod tests;
