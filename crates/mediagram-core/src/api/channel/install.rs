//! Installing a channel's index snapshot: download it, prove it is a
//! library, and only then make it the current catalog.

use std::io::Write;

use grammers_client::Client;
use grammers_client::media::Document;

use super::index::UNREADABLE;
use crate::api::account::revoked::checked;
use crate::api::{Core, CoreError, store};
use crate::versions::{Staging, count_playable};

/// A hard ceiling on the snapshot. A real `library.db` for a few hundred sets
/// is a handful of megabytes; this only stops a wrong or hostile pinned
/// document filling a tablet's storage before anything looks at it.
const MAX_INDEX_BYTES: u64 = 256 * 1024 * 1024;

/// Stages the snapshot, proves it is a library, and only then lets it become
/// the current one.
pub(super) async fn install(
    core: &Core,
    client: &Client,
    document: &Document,
    version: &str,
) -> Result<u64, CoreError> {
    let staging = Staging::begin(&core.installing, &store::dir(core)).await?;
    download(core, client, document, &staging.dir().join(mlib_spec::schema::INDEX_FILE)).await?;
    install_proven(staging, version)
}

/// Makes a downloaded snapshot current once it has proved to be a library.
///
/// Counting is also the check: a file that is not a catalog cannot be
/// counted, and this happens while it is still staged, so a channel with
/// something else pinned in it never replaces a library that works.
fn install_proven(staging: Staging<'_>, version: &str) -> Result<u64, CoreError> {
    let sets = count_playable(staging.dir()).map_err(CoreError::Library(UNREADABLE.into()).logged())?;
    staging.install(version)?;
    Ok(sets)
}

async fn download(
    core: &Core,
    client: &Client,
    document: &Document,
    path: &std::path::Path,
) -> Result<(), CoreError> {
    const WRITING: &str = "writing the downloaded index";
    let mut file = std::fs::File::create(path).map_err(CoreError::io(WRITING))?;
    let mut written: u64 = 0;
    let mut chunks = client.iter_download(document);
    while let Some(chunk) = checked(
        core,
        chunks.next().await,
        |err| CoreError::network("the index download was interrupted")(err),
    )
    .await?
    {
        written += chunk.len() as u64;
        if written > MAX_INDEX_BYTES {
            return Err(CoreError::Library(UNREADABLE.into()));
        }
        file.write_all(&chunk).map_err(CoreError::io(WRITING))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The staged copy is proven to be a library before anything swaps, so a
    /// channel with the wrong document pinned leaves a working catalog alone.
    #[tokio::test]
    async fn a_staged_file_that_is_not_a_library_never_becomes_the_current_one() {
        let dir = tempfile::tempdir().unwrap();
        let core = Core::new(dir.path().display().to_string(), 1, "test-hash".into());
        let root = store::dir(&core);

        let working = Staging::begin(&core.installing, &root).await.unwrap();
        let conn = rusqlite::Connection::open(working.dir().join(mlib_spec::schema::INDEX_FILE)).unwrap();
        for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
            conn.execute(stmt, []).unwrap();
        }
        drop(conn);
        install_proven(working, "v-1").unwrap();
        let before = std::fs::canonicalize(store::current_dir(&core)).unwrap();

        let broken = Staging::begin(&core.installing, &root).await.unwrap();
        std::fs::write(broken.dir().join(mlib_spec::schema::INDEX_FILE), b"not a database").unwrap();
        let refused = install_proven(broken, "v-2").unwrap_err();

        assert_eq!(refused.to_string(), format!("library error: {UNREADABLE}"));
        assert_eq!(std::fs::canonicalize(store::current_dir(&core)).unwrap(), before);
    }
}
