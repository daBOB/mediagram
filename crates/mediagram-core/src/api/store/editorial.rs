//! Building the catalog listing: `dto::summary_from` plus what only a full
//! listing (or [`media_set`]'s single-row form) can attach — subtitles, a
//! summary flag, the genres, age rating, tagline, provider rating,
//! popularity and backdrop a home page's editorial picks need, and the
//! poster, backdrop and season poster resolved to a path on disk. The
//! index's own `artwork` table (custom art the uploader supplies) is read
//! once, into a set of keys — not once per row — and a key's resolution is
//! remembered the first time it is worked out, so a few hundred sets sharing
//! a show's poster and season posters cost that many stats and, for a
//! genuine hit, one table read, not one of each per row. Split out of
//! `store.rs` to keep that file under the line limit.

use std::collections::HashMap;

use rusqlite::Connection;

use crate::catalog::PlayableSet;
use crate::dto::{self, SetSummary};
use crate::versions::{library_db, open_ro};

use super::resolve::resolve_cached;
use super::{Core, CoreError, artwork_dir, current_dir};

pub(in crate::api) fn list_sets(core: &Core) -> Result<Vec<SetSummary>, CoreError> {
    let path = library_db(&current_dir(core));
    if !path.exists() {
        return Ok(Vec::new());
    }
    let conn = open_ro(&path)?;
    let sets =
        crate::catalog::list_playable(&conn).map_err(CoreError::io("reading the catalog"))?;
    enrich(core, &conn, sets)
}

/// One set by id, enriched the same way [`list_sets`] enriches every row —
/// `catalog::playable_set`'s own indexed lookup rather than a full listing
/// searched afterwards. What this actually saves against "list everything,
/// then find one": scanning and enriching every *other* row, resolving
/// artwork for every other row, and — the largest cost in practice — never
/// carrying every other row's whole record back across the UniFFI boundary
/// to a caller that only wanted this one. `enrich`'s own per-listing reads
/// (ratings, genres, subtitles, tagline/rating/franchise facts, and this
/// snapshot's fetched-descriptions sidecar) stay whole-table queries here
/// too, same as for a full listing: cheap enough in SQLite that reading them
/// for one row would cost about what reading them for the whole catalog
/// does, so there is nothing narrowing them would still buy this call.
/// `Ok(None)` both before any catalog is loaded and for an id this one does
/// not hold, matching `list_sets`'s own "nothing installed is an empty
/// answer, not a failure".
pub(in crate::api) fn media_set(core: &Core, set_id: &str) -> Result<Option<SetSummary>, CoreError> {
    let path = library_db(&current_dir(core));
    if !path.exists() {
        return Ok(None);
    }
    let conn = open_ro(&path)?;
    let Some(set) = crate::catalog::playable_set(&conn, set_id)
        .map_err(CoreError::io("reading the catalog"))?
    else {
        return Ok(None);
    };
    Ok(enrich(core, &conn, vec![set])?.into_iter().next())
}

/// `dto::summary_from` plus the facts and resolved artwork only a listing
/// (whole or one row) can attach, read from `conn` — the connection its own
/// caller already opened, so enriching many rows still costs one connection.
fn enrich(core: &Core, conn: &Connection, sets: Vec<PlayableSet>) -> Result<Vec<SetSummary>, CoreError> {
    let ratings =
        crate::shows::certifications(conn).map_err(CoreError::io("reading age ratings"))?;
    let subtitles = crate::catalog_assets::subtitle_languages(conn)
        .map_err(CoreError::io("reading subtitle languages"))?;
    let summarized = crate::catalog_assets::summaries(conn)
        .map_err(CoreError::io("reading which sets have a summary"))?;

    // The index's own genres and facts first; where a row has none, whatever
    // this device fetched fills in. Coarser than `enrich::details::title_info`,
    // which takes an index row whole, so a shelf may show fetched facts
    // beside an index overview. A deliberate difference from the web player,
    // which has no device-side sidecar: a title this device fetched but the
    // index says nothing about still files onto its shelves.
    let mut genres = crate::shows::genres(conn).map_err(CoreError::io("reading genres"))?;
    let mut facts = crate::shows::facts(conn).map_err(CoreError::io("reading show facts"))?;
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
    // Read once for the whole enrichment: which keys this snapshot's
    // `artwork` table actually holds bytes for, so a set whose own key the
    // table does not carry is never queried about at all — see
    // `crate::artwork::keys`.
    let artwork_keys = crate::artwork::keys(conn).unwrap_or_default();
    let mut resolved: HashMap<String, Option<String>> = HashMap::new();

    let mut out = Vec::with_capacity(sets.len());
    for set in &sets {
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
            summary.show_status = row.show_status.clone();
            summary.collection_id = row.collection_id;
            summary.collection_name = row.collection_name.clone();
            summary.series_type = row.series_type.clone();
        }
        resolve_artwork(&mut summary, &version_dir, &artwork, conn, set, &artwork_keys, &mut resolved);
        out.push(summary);
    }
    Ok(out)
}

/// The poster, backdrop and (for an episode) season poster a set's own key
/// names, each resolved at most once per distinct key across the whole
/// enrichment (`resolved`). A poster or a season poster is materialised from
/// the index's `artwork` table on a miss, the same as `store::poster_path`
/// already did per key; a backdrop only ever names a file already on disk.
///
/// That last rule is a known gap from the web player, not parity with it:
/// the web's own `has()` (`web/src/catalog/routes.ts`) counts a backdrop the
/// `artwork` table alone carries, same as it does a poster: an uploader who
/// supplies only a backdrop for a title — no packaged or fetched poster file
/// — shows it on the web and not here. Carried over unchanged from before
/// this pass existed rather than fixed in the same change that batched it;
/// see daBOB/mediagram#1 for the open question of whether to close the gap.
fn resolve_artwork(
    summary: &mut SetSummary,
    version_dir: &std::path::Path,
    artwork_dir: &std::path::Path,
    conn: &Connection,
    set: &PlayableSet,
    artwork_keys: &std::collections::HashSet<String>,
    resolved: &mut HashMap<String, Option<String>>,
) {
    let Some(key) = summary.poster_key.clone() else { return };
    summary.poster_path = resolve_cached(resolved, version_dir, artwork_dir, conn, &key, artwork_keys, true);
    let backdrop_key = mlib_spec::package::backdrop_key(&key);
    summary.backdrop_path =
        resolve_cached(resolved, version_dir, artwork_dir, conn, &backdrop_key, artwork_keys, false);
    if set.kind == "ep"
        && let Some(season) = set.season
    {
        let season_key = mlib_spec::package::season_poster_key(&key, season);
        summary.season_poster_path =
            resolve_cached(resolved, version_dir, artwork_dir, conn, &season_key, artwork_keys, true);
    }
}

#[cfg(test)]
#[path = "editorial_tests.rs"]
mod tests;
