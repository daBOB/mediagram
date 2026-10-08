//! An app release's file lands at its path only once it is whole and matches
//! its caption: written beside it as `.part`, checked, then renamed.

use std::io::Read;
use std::path::Path;

use grammers_mtsender::SenderPoolFatHandle;
use sha2::{Digest, Sha256};

use super::super::download::download_with;
use super::super::responses::Responses;
use crate::api::{Core, CoreError};

pub(super) async fn write_verified(
    core: &Core,
    owner: &SenderPoolFatHandle,
    bytes: u64,
    sha256: &str,
    path: &Path,
    chunks: impl Responses<Item = Vec<u8>>,
) -> Result<(), CoreError> {
    let partial = path.with_extension("part");
    let result = download_with(
        core,
        owner,
        &partial,
        bytes,
        "downloading the app release",
        || CoreError::Io("the app release is larger than its caption says".into()),
        chunks,
    )
    .await
    .and_then(|()| verify(&partial, bytes, sha256));
    match result {
        Ok(()) => std::fs::rename(&partial, path)
            .map_err(CoreError::io("keeping the downloaded app release")),
        Err(err) => {
            let _ = std::fs::remove_file(&partial);
            Err(err)
        }
    }
}

/// Size and sha256 against the caption, then durable — both before the
/// rename that makes the file look ready to install.
fn verify(path: &Path, bytes: u64, sha256: &str) -> Result<(), CoreError> {
    const VERIFYING: &str = "verifying the downloaded app release";
    let mut file = std::fs::File::open(path).map_err(CoreError::io(VERIFYING))?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    let mut copied: u64 = 0;
    loop {
        let n = file.read(&mut buf).map_err(CoreError::io(VERIFYING))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
        copied += n as u64;
    }
    if copied != bytes || hex::encode(hasher.finalize()) != sha256 {
        return Err(CoreError::Io(
            "the downloaded app release does not match its caption".into(),
        ));
    }
    file.sync_all().map_err(CoreError::io(VERIFYING))
}
