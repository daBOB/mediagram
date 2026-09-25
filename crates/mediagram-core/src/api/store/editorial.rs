//! Building the catalog listing: `dto::summary_from` plus what only a full
//! listing can attach — subtitles, a summary flag, and the genres, age
//! rating, tagline, provider rating, popularity and backdrop a home page's
//! editorial picks need, all without a query per row. Split out of
//! `store.rs` to keep that file under the line limit.

use std::path::Path;

use crate::dto::{self, SetSummary};
use crate::versions::{library_db, open_ro};

use super::{Core, CoreError, artwork_dir, current_dir};

pub(in crate::api) fn list_sets(core: &Core) -> Result<Vec<SetSummary>, CoreError> {
    let path = library_db(&current_dir(core));
    if !path.exists() {
        return Ok(Vec::new());
    }
    let conn = open_ro(&path)?;
    let sets =
        crate::catalog::list_playable(&conn).map_err(CoreError::io("reading the catalog"))?;
    let ratings =
        crate::shows::certifications(&conn).map_err(CoreError::io("reading age ratings"))?;
    let subtitles = crate::catalog_assets::subtitle_languages(&conn)
        .map_err(CoreError::io("reading subtitle languages"))?;
    let summarized = crate::catalog_assets::summaries(&conn)
        .map_err(CoreError::io("reading which sets have a summary"))?;

    // The index's own genres and facts first; where a row has none, whatever
    // this device fetched fills in. Coarser than `enrich::details::title_info`,
    // which takes an index row whole, so a shelf may show fetched facts
    // beside an index overview. A deliberate difference from the web player,
    // which has no device-side sidecar: a title this device fetched but the
    // index says nothing about still files onto its shelves.
    let mut genres = crate::shows::genres(&conn).map_err(CoreError::io("reading genres"))?;
    let mut facts = crate::shows::facts(&conn).map_err(CoreError::io("reading show facts"))?;
    if let Some(fetched) = crate::api::enrich::details::open_fetched_ro(core) {
        // Tolerant, unlike the index reads above: this store is only ever a
        // fallback, so a sidecar that cannot be read — a partial file a
        // rolled-back migration left behind, say — must not take the whole
        // catalog down over facts it was never depended on for.
        match crate::shows::genres(&fetched) {
            Ok(more) => {
                for (key, list) in more {
                    genres.entry(key).or_insert(list);
                }
            }
            Err(err) => tracing::warn!(error = %err, "fetched genres could not be read"),
        }
        match crate::shows::facts(&fetched) {
            Ok(more) => {
                for (key, row) in more {
                    facts.entry(key).or_insert(row);
                }
            }
            Err(err) => tracing::warn!(error = %err, "fetched show facts could not be read"),
        }
    }

    let version_dir = current_dir(core);
    let artwork = artwork_dir(core);
    Ok(sets
        .iter()
        .map(|set| {
            let mut summary = dto::summary_from(set);
            summary.fsk = summary.poster_key.as_ref().and_then(|key| ratings.get(key).cloned());
            summary.genres = summary
                .poster_key
                .as_ref()
                .and_then(|key| genres.get(key).cloned())
                .unwrap_or_default();
            summary.subtitles = subtitles.get(&set.set_id).cloned().unwrap_or_default();
            summary.has_summary = summarized.contains(&set.set_id);
            if let Some(row) = summary.poster_key.as_ref().and_then(|key| facts.get(key)) {
                summary.tagline = row.tagline.clone();
                summary.rating = row.rating;
                summary.popularity = row.popularity;
            }
            summary.backdrop_key = summary
                .poster_key
                .as_deref()
                .and_then(|key| backdrop_if_present(&version_dir, &artwork, key));
            summary
        })
        .collect())
}

/// The backdrop key a title's poster key names, but only when that file
/// actually sits on disk — matching the web player's `routes.ts`, which
/// checks its poster store before ever naming one, rather than assuming a
/// key that resolves means a picture that exists.
fn backdrop_if_present(version_dir: &Path, artwork_dir: &Path, poster_key: &str) -> Option<String> {
    let key = mlib_spec::package::backdrop_key(poster_key);
    let name = format!("{key}.jpg");
    (version_dir.join("posters").join(&name).exists() || artwork_dir.join(&name).exists())
        .then_some(key)
}
