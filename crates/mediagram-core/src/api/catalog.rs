//! Catalog refresh and lookups: downloading and decrypting the published
//! package, and answering what it holds.
//!
//! Refresh always extracts into the same on-disk directory, so a later call
//! reopens the same `library.db` without Core needing to remember a path.

use std::path::{Path, PathBuf};

use rusqlite::Connection;

use crate::catalog as queries;
use crate::dto::{self, SetSummary};
use crate::package::{self, PackageError};

use super::{Core, CoreError};

const DIRNAME: &str = "catalog";

pub(super) fn dir(core: &Core) -> PathBuf {
    core.data_dir.join(DIRNAME)
}

fn library_db(core: &Core) -> PathBuf {
    dir(core).join("library.db")
}

/// Opens the extracted catalog, or reports it as not-yet-loaded rather than
/// creating an empty database — `rusqlite::Connection::open` would happily
/// do the latter for a path that does not exist yet.
pub(super) fn open(core: &Core) -> Result<Connection, CoreError> {
    let path = library_db(core);
    if !path.exists() {
        return Err(CoreError::NotFound("no catalog is loaded yet".into()));
    }
    Connection::open(&path).map_err(|_| CoreError::Io("opening the catalog".into()))
}

pub(super) async fn refresh_catalog(
    core: &Core,
    pointer_url: String,
    key_b64: String,
) -> Result<u64, CoreError> {
    let key =
        package::cipher::parse_key(&key_b64).map_err(|_| CoreError::Cipher("bad key".into()))?;
    let http = reqwest::Client::new();

    let pointer: mlib_spec::package::LatestPointer = fetch(&http, &pointer_url)
        .await?
        .json()
        .await
        .map_err(|_| CoreError::Network("pointer response was not valid JSON".into()))?;
    let sealed = fetch(&http, &pointer.url).await?.bytes().await.map_err(|_| {
        CoreError::Network("the archive download ended before it finished".into())
    })?;

    let db = package::read_package(&pointer, &sealed, &key, &dir(core)).map_err(package_error)?;
    let conn =
        Connection::open(&db).map_err(|_| CoreError::Io("opening the refreshed catalog".into()))?;
    let count = queries::list_playable(&conn)
        .map_err(|_| CoreError::Io("reading the refreshed catalog".into()))?
        .len();
    Ok(count as u64)
}

async fn fetch(http: &reqwest::Client, url: &str) -> Result<reqwest::Response, CoreError> {
    http.get(url)
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|_| CoreError::Network(format!("request to {url} failed")))
}

fn package_error(err: PackageError) -> CoreError {
    match err {
        PackageError::Cipher(_) => CoreError::Cipher("package failed authentication".into()),
        PackageError::DigestMismatch | PackageError::Pointer(_) | PackageError::Archive(_) => {
            CoreError::Cipher("package failed verification".into())
        }
        PackageError::Io(msg) => CoreError::Io(msg),
    }
}

pub(super) fn list_sets(core: &Core) -> Result<Vec<SetSummary>, CoreError> {
    let path = library_db(core);
    if !path.exists() {
        return Ok(Vec::new());
    }
    let conn =
        Connection::open(&path).map_err(|_| CoreError::Io("opening the catalog".into()))?;
    let sets = queries::list_playable(&conn).map_err(|_| CoreError::Io("reading the catalog".into()))?;
    Ok(sets.iter().map(dto::summary_from).collect())
}

pub(super) fn poster_path(core: &Core, poster_key: String) -> Option<String> {
    if !mlib_spec::package::poster_key_is_valid(&poster_key) {
        return None;
    }
    let path: PathBuf = dir(core).join("posters").join(format!("{poster_key}.jpg"));
    path_if_exists(&path)
}

fn path_if_exists(path: &Path) -> Option<String> {
    path.exists().then(|| path.display().to_string())
}

pub(super) fn total_size(core: &Core, set_id: String) -> Result<u64, CoreError> {
    let conn = open(core)?;
    queries::playable_set(&conn, &set_id)
        .map_err(|_| CoreError::Io("reading the catalog".into()))?
        .map(|set| set.total)
        .ok_or_else(|| CoreError::NotFound("set not found".into()))
}
