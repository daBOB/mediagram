//! The capped chunk-download loop that writes a pinned document to a file.
//! Three things arrive through it: the index snapshot ([`super::install`]), a
//! subtitle bundle (`fetch_into` in `crate::api::subtitles_cache_download`)
//! and the app release APK (`write_verified` in [`super::app_release`]).
//! State sync's watch-state documents do not: they are read into memory by
//! their own loop (`download_capped` in
//! `crate::api::state_sync::telegram_channel`), which skips an oversized
//! document instead of failing.

use std::io::Write;
use std::path::Path;

use grammers_mtsender::SenderPoolFatHandle;

use super::responses::Responses;
use crate::api::account::revoked::checked_for;
use crate::api::{Core, CoreError};

/// Downloads `chunks` into `path`, refusing once more than `max_bytes` has
/// arrived — the only thing standing between a wrong or hostile pinned
/// document and filling a device's storage before anything looks at what it
/// downloaded. `what` labels a write failure and an interrupted download
/// alike; `oversize` builds the error a caller wants for going past the cap,
/// since each caller words its refusal differently.
pub(in crate::api) async fn download_with(
    core: &Core,
    owner: &SenderPoolFatHandle,
    path: &Path,
    max_bytes: u64,
    what: &str,
    oversize: impl FnOnce() -> CoreError,
    mut chunks: impl Responses<Item = Vec<u8>>,
) -> Result<(), CoreError> {
    let mut file = std::fs::File::create(path).map_err(CoreError::io(what))?;
    let mut written: u64 = 0;
    while let Some(chunk) = checked_for(core, owner, chunks.next_response().await, |err| {
        CoreError::network(what)(err)
    })
    .await?
    {
        written += chunk.len() as u64;
        if written > max_bytes {
            return Err(oversize());
        }
        file.write_all(&chunk).map_err(CoreError::io(what))?;
    }
    Ok(())
}

#[cfg(test)]
#[path = "download_tests.rs"]
mod tests;
