//! `Core::title_credits`/`person`/`franchises`/`search_people`/
//! `fetch_portrait`: the departments the web player's `catalog/credits.ts`
//! reads, read here the same way and carried across the UniFFI boundary.
//!
//! A portrait is fetched lazily, one person at a time, when a Cast row or a
//! person page first shows someone this device has no file for — never in
//! bulk, and never gated on a TMDB key: the image CDN is public, the same
//! reason `posters::poster_url` needs none either.

use std::sync::Arc;

use mediagram_tmdb::posters::{PORTRAIT_WIDTH, PosterRef};

use crate::dto::{CreditRecord, FranchiseRecord, PeopleHitRecord, PersonRecord, TitleCreditsRecord};
use crate::shows::title_of;

use super::{Core, store};

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// A title's cast, in billing order, apart from its crew. Empty for a
    /// key this device cannot parse, or an index with no `credits` table
    /// (v8 and older, or none installed yet) — neither is an error.
    pub async fn title_credits(self: Arc<Self>, key: String) -> TitleCreditsRecord {
        self.blocking(move |core| run_title_credits(core, &key)).await
    }

    /// One person and the keys of every title they are credited on, or
    /// `None` when nobody by this id is credited on anything the index holds.
    pub async fn person(self: Arc<Self>, person_id: u64) -> Option<PersonRecord> {
        self.blocking(move |core| run_person(core, person_id)).await
    }

    /// Every film franchise the index names, alphabetically.
    pub async fn franchises(self: Arc<Self>) -> Vec<FranchiseRecord> {
        self.blocking(run_franchises).await
    }

    /// People whose name matches every word of `query`, most-credited first
    /// — see [`crate::credits::people_matching`].
    pub async fn search_people(self: Arc<Self>, query: String) -> Vec<PeopleHitRecord> {
        self.blocking(move |core| run_search_people(core, &query)).await
    }

    /// Downloads this person's portrait (w185) from the profile path the
    /// index — or, failing that, this device's own fetched descriptions —
    /// recorded for them, into the artwork directory `poster_path` already
    /// searches, and returns the file's path.
    ///
    /// Idempotent: a file already on disk is returned without another
    /// request. `None` when nobody recorded a profile for this person, or
    /// the download failed — a missing face is a cosmetic loss, never an
    /// error a caller must handle.
    pub async fn fetch_portrait(self: Arc<Self>, person_id: u64) -> Option<String> {
        let key = format!("tmdb-person-{person_id}");
        if let Some(path) = store::poster_path(&self, key.clone()) {
            return Some(path);
        }
        let profile = self.blocking(move |core| profile_of(core, person_id)).await?;
        let client = crate::http::client().ok()?;
        let poster = PosterRef { key: key.clone(), path: profile, backdrop_width: Some(PORTRAIT_WIDTH) };
        let dir = store::artwork_dir(&self);
        let written = mediagram_tmdb::poster_files::download_into(&client, std::slice::from_ref(&poster), &dir)
            .await
            .unwrap_or_else(|err| {
                tracing::warn!(error = %err, "the portrait could not be downloaded");
                Vec::new()
            });
        written.contains(&key).then(|| dir.join(format!("{key}.jpg")).display().to_string())
    }
}

fn run_title_credits(core: &Core, key: &str) -> TitleCreditsRecord {
    let Some((kind, id)) = title_of(key) else {
        return TitleCreditsRecord::default();
    };
    let Ok(conn) = store::open(core) else {
        return TitleCreditsRecord::default();
    };
    let credits = crate::credits::for_title(&conn, kind, id).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "a title's credits could not be read");
        Default::default()
    });
    TitleCreditsRecord {
        cast: credits.cast.into_iter().map(|c| shape(core, c)).collect(),
        crew: credits.crew.into_iter().map(|c| shape(core, c)).collect(),
    }
}

fn shape(core: &Core, credited: crate::credits::Credited) -> CreditRecord {
    CreditRecord {
        portrait_key: portrait_if_held(core, credited.person_id),
        person_id: credited.person_id,
        name: credited.name,
        role: credited.role,
    }
}

fn run_person(core: &Core, person_id: u64) -> Option<PersonRecord> {
    let conn = store::open(core).ok()?;
    let found = crate::credits::for_person(&conn, person_id)
        .inspect_err(|err| tracing::warn!(error = %err, "a person's credits could not be read"))
        .ok()??;
    Some(PersonRecord {
        portrait_key: portrait_if_held(core, person_id),
        person_id,
        name: found.name,
        title_keys: found.title_keys,
    })
}

fn run_franchises(core: &Core) -> Vec<FranchiseRecord> {
    let Ok(conn) = store::open(core) else {
        return Vec::new();
    };
    crate::franchises::all(&conn)
        .unwrap_or_else(|err| {
            tracing::warn!(error = %err, "franchises could not be read");
            Vec::new()
        })
        .into_iter()
        .map(|f| FranchiseRecord { id: f.id, name: f.name, overview: f.overview })
        .collect()
}

fn run_search_people(core: &Core, query: &str) -> Vec<PeopleHitRecord> {
    let Ok(conn) = store::open(core) else {
        return Vec::new();
    };
    crate::credits::people_matching(&conn, query)
        .unwrap_or_else(|err| {
            tracing::warn!(error = %err, "people search could not be read");
            Vec::new()
        })
        .into_iter()
        .map(|hit| PeopleHitRecord {
            portrait_key: portrait_if_held(core, hit.person_id),
            person_id: hit.person_id,
            name: hit.name,
            title_keys: hit.title_keys,
        })
        .collect()
}

/// The key this person's portrait would be held under, present only when
/// the file already exists on this device — see `SetSummary::backdrop_key`.
fn portrait_if_held(core: &Core, person_id: u64) -> Option<String> {
    let key = format!("tmdb-person-{person_id}");
    store::poster_path(core, key.clone()).map(|_| key)
}

/// The profile path to fetch a portrait from: the index's own `credits`
/// first, then this device's own fetched descriptions — the same order
/// `enrich::details::title_info` prefers the index in.
fn profile_of(core: &Core, person_id: u64) -> Option<String> {
    if let Ok(conn) = store::open(core)
        && let Some(path) = crate::credits::profile_of(&conn, person_id).ok().flatten()
    {
        return Some(path);
    }
    let fetched = super::enrich::details::open_fetched_ro(core)?;
    crate::credits::profile_of(&fetched, person_id).ok().flatten()
}

#[cfg(test)]
#[path = "credits_tests.rs"]
mod tests;
