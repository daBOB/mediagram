//! Downloading one bundle into the cache: a temporary file of its own, the
//! checksum the index declared, durable storage, then the rename that makes
//! it visible to every other reader. The only part of the cache that
//! reaches Telegram.

use std::path::{Path, PathBuf};

use sha2::{Digest, Sha256};

use crate::api::account::session;
use crate::api::channel::download::download_with;
use crate::api::channel::library;
use crate::api::{Core, CoreError};
use crate::catalog_subtitles::BundleRef;
use crate::transport::stream::part_document;

use super::dir;

fn tmp_path(core: &Core, sha: &str) -> PathBuf {
    let mut rand = [0u8; 8];
    getrandom::fill(&mut rand).expect("the OS random source is available");
    dir(core).join(format!(
        "{sha}.{}.{}.tmp",
        std::process::id(),
        hex::encode(rand)
    ))
}

/// Downloads `bundle`'s document into a temp file, verifies its checksum and
/// durability, then publishes it under its sha — replacing a stale or
/// corrupt cached file the same rename atomically supersedes.
pub(super) async fn fetch_into(
    core: &Core,
    bundle: &BundleRef,
    final_path: &Path,
) -> Result<(), CoreError> {
    let (client, owner) = session::connection(core).await;
    let handles = library::read(&library::path(core))?;
    let channel = library::peer_for_chat(&handles, bundle.chat_id).ok_or_else(|| {
        CoreError::NotFound(
            "this device has no way to reach the channel a subtitle bundle is in".into(),
        )
    })?;
    let document = part_document(&client, channel, bundle.message_id)
        .await
        .map_err(CoreError::network("resolving a subtitle bundle"))?;

    std::fs::create_dir_all(dir(core)).map_err(CoreError::io("preparing the subtitle cache"))?;
    let tmp = tmp_path(core, &bundle.sha256);
    let result = download_with(
        core,
        &owner,
        &tmp,
        bundle.bytes,
        "downloading a subtitle bundle",
        || CoreError::Io("a subtitle bundle is larger than the index says".into()),
        client.iter_download(&document),
    )
    .await
    .and_then(|()| verify_and_sync(&tmp, &bundle.sha256));

    match result {
        Ok(()) => {
            std::fs::rename(&tmp, final_path).map_err(CoreError::io("caching a subtitle bundle"))
        }
        Err(err) => {
            let _ = std::fs::remove_file(&tmp);
            Err(err)
        }
    }
}

/// Checks a freshly downloaded file's sha256 against what the index
/// declared, then forces it to durable storage — both ahead of the rename
/// that makes it visible to every other reader.
fn verify_and_sync(path: &Path, expected_sha256: &str) -> Result<(), CoreError> {
    const VERIFYING: &str = "verifying a downloaded subtitle bundle";
    let bytes = std::fs::read(path).map_err(CoreError::io(VERIFYING))?;
    if hex::encode(Sha256::digest(&bytes)) != expected_sha256 {
        return Err(CoreError::Io(
            "a subtitle bundle's checksum did not match the index".into(),
        ));
    }
    std::fs::File::open(path)
        .and_then(|file| file.sync_all())
        .map_err(CoreError::io(VERIFYING))
}
