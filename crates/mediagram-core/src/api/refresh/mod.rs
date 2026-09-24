//! Turning a pointer into a usable catalog: replay refusal, a size ceiling
//! enforced against the actual bytes, atomic staging, and manifest
//! cross-checking. `docs/mlib-package-v1.md` §5 is the normative algorithm;
//! this is that algorithm, not a simplified reading of it. The network and
//! manifest-checking parts live in [`download`]; installing the result is
//! [`crate::versions`]'s, the same as for a channel's index.

mod download;

use mlib_spec::package::LatestPointer;

use crate::api::{Core, CoreError, store};
use crate::http;
use crate::package::{self, PackageError};
use crate::versions::identity::{identity_of, read_identity, write_identity};
use crate::versions::{FUTURE_TOLERANCE_SECONDS, Staging, count_playable, now_unix};
use download::{check_manifest, fetch, fetch_capped, package_url};

pub(super) async fn refresh_catalog(
    core: &Core,
    pointer_url: String,
    key_b64: String,
) -> Result<u64, CoreError> {
    let key = package::cipher::parse_key(&key_b64)
        .map_err(CoreError::Cipher("bad key".into()).logged())?;
    let client = http::client()?;

    let pointer: LatestPointer = fetch(&client, &pointer_url)
        .await?
        .json()
        .await
        .map_err(CoreError::network("pointer response was not valid JSON"))?;

    mlib_spec::package::pointer_is_readable(&pointer, package::SUPPORTED_SCHEMA)
        .map_err(|err| CoreError::Cipher(err.to_string()))?;

    // Before the download: a package sealed for another key cannot open, and
    // finding that out after fetching it wastes the fetch.
    if pointer.key_id != mlib_spec::package::key_id(&key) {
        return Err(CoreError::Cipher(
            "the package was sealed for a different key".into(),
        ));
    }
    if pointer.created_at > now_unix() + FUTURE_TOLERANCE_SECONDS {
        return Err(CoreError::Cipher(
            "the package claims to have been built in the future".into(),
        ));
    }

    let current = store::current_dir(core);
    if let Some(count) = check_replay(&current, &pointer)? {
        return Ok(count);
    }

    let url = package_url(&pointer_url, &pointer.file)?;
    let cap = pointer.bytes.min(mlib_spec::package::MAX_PACKAGE_BYTES);
    let sealed = fetch_capped(&client, &url, cap).await?;

    let root = store::dir(core);
    let staging = Staging::begin(&core.installing, &root).await?;
    // Another download may have published while this one was in flight.
    // Recheck while holding the installation turn, through publication.
    if let Some(count) = check_replay(&current, &pointer)? {
        return Ok(count);
    }
    // Checks the sealed bytes against the pointer's sha256 before opening
    // them, so nothing here needs to.
    package::read_package(&pointer, &sealed, &key, staging.dir()).map_err(package_error)?;
    check_manifest(staging.dir(), &pointer)?;
    // Authentication proves who sealed the bytes, not that SQLite can read
    // them. Query while staged, before installation removes the held copy.
    let count = count_playable(staging.dir())?;
    write_identity(staging.dir(), &identity_of(&pointer))?;

    staging.install(&format!("v-{}", pointer.created_at))?;
    Ok(count)
}

fn check_replay(
    current: &std::path::Path,
    pointer: &LatestPointer,
) -> Result<Option<u64>, CoreError> {
    let Some(held) = read_identity(current)? else {
        return Ok(None);
    };
    // Never decided from `sha256`: that field is unauthenticated, so a host
    // could copy the held digest to suppress an update without cipher checks.
    if held == identity_of(pointer) {
        return count_playable(current).map(Some);
    }
    if pointer.created_at <= held.created_at {
        return Err(CoreError::Cipher(
            "the package offered is older than the one already held".into(),
        ));
    }
    Ok(None)
}

fn package_error(err: PackageError) -> CoreError {
    match err {
        cause @ PackageError::Cipher(_) => {
            CoreError::Cipher("package failed authentication".into()).logged()(cause)
        }
        PackageError::DigestMismatch => {
            CoreError::Cipher("the package does not match the sha256 in its pointer".into())
        }
        cause @ (PackageError::Pointer(_) | PackageError::Archive(_)) => {
            CoreError::Cipher("package failed verification".into()).logged()(cause)
        }
        PackageError::Io(msg) => CoreError::Io(msg),
    }
}

#[cfg(test)]
#[path = "refresh_tests.rs"]
mod tests;
