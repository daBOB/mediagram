//! Deciding what one fetch run will ask a catalog's provider for.
//!
//! Split so each part can be driven by a stub in tests, and so the two
//! decisions worth pinning are readable from outside: [`plan_fetch`] settles
//! what a run asks for, in which language, and where what comes back goes,
//! before the key is spent; [`verify_then_fetch`] puts the key check ahead
//! of [`super::fetch::fetch_into`] and the disk cache around it, in that
//! order and for the reason its own comment gives. [`fetch_posters`]
//! composes them around a real TMDB client and decides nothing else.

use std::path::{Path, PathBuf};

use mlib_spec::Kind;
use rusqlite::{Connection, OpenFlags};

use mediagram_tmdb::tmdb_client::{DiskCachedApi, Localized, TmdbApi, TmdbClient};

use crate::catalog::PlayableSet;
use crate::dto::PosterReport;

use super::catalog;
use super::fetch::{fetch_into, language_of};
use super::{Core, CoreError, http};

/// What a fetch will ask the provider for, and where what comes back is
/// kept.
///
/// The directory is part of this answer rather than chosen where the writing
/// happens, so that it is a value a test can read. Where artwork lands is
/// the whole of a defect this crate has shipped once already.
pub struct FetchPlan {
    pub artwork_dir: PathBuf,
    pub titles: Vec<(Kind, u64)>,
    pub without_id: u32,
    /// The language to ask in — see [`language_of`]. Settled here, from the
    /// same snapshot of the index the titles come from, because it is part
    /// of what a run asks for and not a detail of how it asks.
    pub language: String,
}

/// Reads the installed catalog and works out what a fetch would do with it.
///
/// `current` is a symlink a refresh swaps atomically, then deletes the
/// directory it used to point at. `std::fs::canonicalize` resolves it to
/// the real directory exactly once, here, before any request, and that
/// resolved value is used for exactly one thing afterwards: opening
/// `library.db`, which genuinely belongs to this snapshot of the catalog.
/// A refresh landing mid-fetch therefore leaves this run reading the
/// database that was current when it started, whole, rather than reading
/// out of a directory a cleanup pass deletes out from under it.
///
/// The posters a fetch writes and the TMDB disk cache it reads through go
/// to `catalog::artwork_dir`: beside the version directories, not inside
/// one, so no refresh reaches them — and inside `catalog/`, so forgetting
/// the library forgets them too.
///
/// `fallback` is the caller's own locale, used only for a library that has
/// never been described in any language.
pub fn plan_fetch(core: &Core, fallback: &str) -> Result<FetchPlan, CoreError> {
    let dir = std::fs::canonicalize(catalog::current_dir(core))
        .map_err(|_| CoreError::NotFound("no catalog is loaded yet".into()))?;
    let conn = Connection::open_with_flags(dir.join("library.db"), OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| CoreError::Io("opening the catalog".into()))?;
    let sets = crate::catalog::list_playable(&conn)
        .map_err(|_| CoreError::Io("reading the catalog".into()))?;
    let language = language_of(&conn, fallback);
    drop(conn);

    let (titles, without_id) = split_titles(&sets);
    Ok(FetchPlan { artwork_dir: catalog::artwork_dir(core), titles, without_id, language })
}

/// Fetches artwork for every title the provider numbers.
pub(super) async fn fetch_posters(
    core: &Core,
    tmdb_key: String,
    language: String,
) -> Result<PosterReport, CoreError> {
    let plan = plan_fetch(core, &language)?;
    if plan.titles.is_empty() {
        // Nothing the provider could answer about — no reason to spend a
        // request validating a key that will never be used.
        return Ok(PosterReport { no_provider_id: plan.without_id, ..PosterReport::default() });
    }

    // Built once, and handed on: `TmdbClient` takes this same instance
    // rather than building its own (see `mediagram_tmdb::tmdb_client`'s doc
    // comment), so there is one client here, not two, and only `client()`'s
    // own call to `install_provider` to account for.
    let client = http::client()?;
    let api = TmdbClient::new(client.clone(), tmdb_key);
    verify_then_fetch(
        api,
        &client,
        &plan.artwork_dir,
        &plan.language,
        &plan.titles,
        plan.without_id,
    )
    .await
}

/// Validates the key against the provider, then resolves and downloads with
/// it.
///
/// `api` is the provider itself, and the validation is asked of it directly.
/// Asked instead through the disk cache built below, a warm cache would
/// answer `/authentication` out of a file some earlier run wrote — the cache
/// keys on the endpoint and the query, and the key appears in neither — so a
/// rotated or mistyped key would pass validation without ever having been
/// presented, and every already-resolved title would then be served from
/// that same cache. The run would report a library that is entirely
/// "already held" and a key that is entirely fine.
///
/// The cache wraps `api` afterwards, for the resolve-and-download half,
/// where being answered twice out of one request is the whole point of it.
pub async fn verify_then_fetch<A: TmdbApi>(
    api: A,
    http: &reqwest::Client,
    artwork_dir: &Path,
    language: &str,
    titles: &[(Kind, u64)],
    without_id: u32,
) -> Result<PosterReport, CoreError> {
    verify_key(&api).await?;
    let cached = Localized::new(DiskCachedApi::new(api, artwork_dir), language);
    Ok(fetch_into(&cached, http, artwork_dir, titles, without_id).await)
}

/// Splits the catalog into what the provider can be asked about and what
/// cannot be asked at all — a course has no provider id, and is counted
/// rather than looked up. `pub` because the count it returns is part of what
/// a fetch reports, and a caller needs this classification, not a copy of it.
pub fn split_titles(sets: &[PlayableSet]) -> (Vec<(Kind, u64)>, u32) {
    let mut titles = Vec::new();
    let mut without_id = 0u32;
    for set in sets {
        match (kind_of(&set.kind), set.tmdb) {
            (Some(kind), Some(id)) if id > 0 => titles.push((kind, id as u64)),
            _ => without_id += 1,
        }
    }
    (titles, without_id)
}

/// The `kind` column spells `Kind` exactly as its own serde does — see
/// `mlib_spec::Kind`'s `rename_all = "lowercase"`.
fn kind_of(kind: &str) -> Option<Kind> {
    match kind {
        "movie" => Some(Kind::Movie),
        "ep" => Some(Kind::Ep),
        "tut" => Some(Kind::Tut),
        "doc" => Some(Kind::Doc),
        _ => None,
    }
}

/// Validates the key against TMDB before any resolve or download, so a
/// wrong key is reported once rather than discovered as a report full of
/// zeroes a viewer might retry forever. Reaches TMDB every time it is
/// asked — see [`verify_then_fetch`] for what it is deliberately not asked
/// through.
async fn verify_key(api: &impl TmdbApi) -> Result<(), CoreError> {
    match api.get_json("/authentication", &[]).await {
        Ok(_) => Ok(()),
        Err(err) if rejected_the_key(&err) => {
            Err(CoreError::NotAuthorized("the artwork provider rejected this key".into()))
        }
        Err(_) => Err(CoreError::Network("could not reach the artwork provider".into())),
    }
}

/// TMDB answers an invalid key with HTTP 401. `TmdbClient::get_json` strips
/// the request URL — the only place the key appears — before formatting any
/// error, so this string carries only the endpoint path and the status,
/// never the key itself. The status is not a bare number: `StatusCode`'s
/// `Display` renders `"401 Unauthorized"`, and `"failed with 401"` matches
/// it as a prefix — pinned by the tests below against the exact `bail!` in
/// `mediagram_tmdb::tmdb_client::TmdbClient::get_json`, so a reword there
/// fails a test here instead of silently turning a rejected key into a
/// retry loop.
fn rejected_the_key(err: &anyhow::Error) -> bool {
    err.to_string().contains("failed with 401")
}

#[cfg(test)]
#[path = "artwork_tests.rs"]
mod tests;
