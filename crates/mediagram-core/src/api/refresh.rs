//! Turning a pointer into a usable catalog: replay refusal, a size ceiling
//! enforced against the actual bytes, atomic staging, and manifest
//! cross-checking. `docs/mlib-package-v1.md` §5 is the normative algorithm;
//! this is that algorithm, not a simplified reading of it. The network and
//! manifest-checking parts live in [`super::refresh_fetch`]; this file is
//! the orchestration and the on-disk staging.

use std::time::{SystemTime, UNIX_EPOCH};

use mlib_spec::package::LatestPointer;
use sha2::{Digest, Sha256};

use super::refresh_fetch::{check_manifest, fetch, fetch_capped, package_url};
use super::{Core, CoreError, catalog, http};
use crate::package::{self, PackageError};

/// How far ahead of now a package may claim to have been built. Clocks
/// disagree by minutes, not days; a package dated next year is either a
/// mistake or an attempt to make every later one look stale.
const FUTURE_TOLERANCE_SECONDS: i64 = 24 * 60 * 60;

pub(super) async fn refresh_catalog(
    core: &Core,
    pointer_url: String,
    key_b64: String,
) -> Result<u64, CoreError> {
    let key =
        package::cipher::parse_key(&key_b64).map_err(|_| CoreError::Cipher("bad key".into()))?;
    let client = http::client()?;

    let pointer: LatestPointer = fetch(&client, &pointer_url)
        .await?
        .json()
        .await
        .map_err(|_| CoreError::Network("pointer response was not valid JSON".into()))?;

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

    let current = catalog::current_dir(core);
    if let Some(held) = catalog::read_identity(&current) {
        let offered = catalog::identity_of(&pointer);
        // Never decided from `sha256`: that field is unauthenticated, so a
        // host that wants to suppress an update could set it to the digest
        // of the copy already held, and a reader that skips on that match
        // would never run the cipher and never notice.
        if held == offered {
            return catalog::count_playable(&current);
        }
        if pointer.created_at <= held.created_at {
            return Err(CoreError::Cipher(
                "the package offered is older than the one already held".into(),
            ));
        }
    }

    let url = package_url(&pointer_url, &pointer.file)?;
    let cap = pointer.bytes.min(mlib_spec::package::MAX_PACKAGE_BYTES);
    let sealed = fetch_capped(&client, &url, cap).await?;

    let digest = hex::encode(Sha256::digest(&sealed));
    if digest != pointer.sha256 {
        return Err(CoreError::Cipher(
            "the package does not match the sha256 in its pointer".into(),
        ));
    }

    let root = catalog::dir(core);
    let incoming = root.join("incoming");
    let _ = std::fs::remove_dir_all(&incoming);
    package::read_package(&pointer, &sealed, &key, &incoming).map_err(package_error)?;
    check_manifest(&incoming, &pointer)?;
    catalog::write_identity(&incoming, &catalog::identity_of(&pointer))?;

    let version_name = format!("v-{}", pointer.created_at);
    let version = root.join(&version_name);
    let _ = std::fs::remove_dir_all(&version);
    std::fs::rename(&incoming, &version)
        .map_err(|_| CoreError::Io("staging the refreshed catalog".into()))?;
    swap_current(core, &version_name)?;
    remove_other_versions(core, &version_name)?;

    catalog::count_playable(&version)
}

/// Points `current` at `version_name`, atomically: a symlink renamed over
/// another is a single filesystem operation, so a reader that dies mid
/// refresh is looking at one whole catalog or the other, never at half of
/// each, and a handle already open keeps the version it opened.
fn swap_current(core: &Core, version_name: &str) -> Result<(), CoreError> {
    let root = catalog::dir(core);
    let staged = root.join(".current-swap");
    let _ = std::fs::remove_file(&staged);
    std::os::unix::fs::symlink(version_name, &staged)
        .map_err(|_| CoreError::Io("staging the catalog switch".into()))?;
    std::fs::rename(&staged, root.join(catalog::CURRENT))
        .map_err(|_| CoreError::Io("switching the current catalog".into()))
}

/// Clears every stale version and leftover staging directory, keeping only
/// the one just published and whatever `current` points at.
fn remove_other_versions(core: &Core, keep: &str) -> Result<(), CoreError> {
    let root = catalog::dir(core);
    let entries = match std::fs::read_dir(&root) {
        Ok(entries) => entries,
        Err(_) => return Ok(()),
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(name) = name.to_str() else { continue };
        if name == keep || name == catalog::CURRENT {
            continue;
        }
        if name.starts_with("v-") || name == "incoming" {
            let _ = std::fs::remove_dir_all(entry.path());
        }
    }
    Ok(())
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

fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}
