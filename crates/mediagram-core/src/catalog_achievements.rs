//! What the achievement rules need from the installed catalog: each
//! playable set's kind, genres and collection, and each show's episodes and
//! course's lessons. A port of `web/src/state/achievement-library.ts` — both
//! read the same channel index, so both surfaces count one library alike.
//!
//! Genres are the index's own `shows` rows only, not the ones this device
//! fetched into its sidecar that the Android shelves also show
//! (`api::store::editorial`): the web player has no sidecar, and an
//! achievement one surface shows and the other does not would be exactly the
//! divergence the two are held together against.

use std::collections::HashMap;

use mediagram_tmdb::posters::poster_key;
use mlib_spec::Kind;
use rusqlite::Connection;

use crate::state::stats::achievements::{LibraryCollection, LibraryTitle};

/// Every playable set as the rules read it, and the collections they form.
pub fn library_facts(conn: &Connection) -> anyhow::Result<(Vec<LibraryTitle>, Vec<LibraryCollection>)> {
    let genres = crate::shows::genres(conn)?;
    let mut members: HashMap<String, Vec<String>> = HashMap::new();
    let mut library = Vec::new();
    for set in crate::catalog::list_playable(conn)? {
        // The key the catalog files a title's provider facts under, so a
        // title counts the genres its own page shows. Only a provider id
        // has any: a course or an untagged film has none to count.
        let key = set.tmdb.map(|id| poster_key(set.kind.parse().unwrap_or(Kind::Ep), id));
        let collection = collection_of(&set.kind, set.show.as_deref());
        if let Some(id) = &collection {
            members.entry(id.clone()).or_default().push(set.set_id.clone());
        }
        library.push(LibraryTitle {
            genres: key.and_then(|key| genres.get(&key).cloned()).unwrap_or_default(),
            set_id: set.set_id,
            kind: set.kind,
            collection,
        });
    }
    let collections = members.into_iter().map(|(id, set_ids)| LibraryCollection { id, set_ids }).collect();
    Ok((library, collections))
}

/// A show's episodes or a course's lessons, by name, the way the shelves
/// group them. A course's documents are not lessons, and a set with no show
/// belongs to none: "Unknown show" is a shelf to file it on, not a series to
/// finish.
pub fn collection_of(kind: &str, show: Option<&str>) -> Option<String> {
    let show = show.filter(|show| !show.is_empty())?;
    matches!(kind, "ep" | "tut").then(|| format!("{kind}:{show}"))
}

#[cfg(test)]
#[path = "catalog_achievements_tests.rs"]
mod tests;
