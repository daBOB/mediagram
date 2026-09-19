//! Reads a downloaded, sealed package into an on-disk index.
//!
//! A package is decrypted whole, never streamed — [`cipher::open`] already
//! enforces this, and its tag is checked before any plaintext exists, so a
//! caller never unpacks bytes it has not authenticated. The pointer's digest
//! is checked first, ahead of the cipher, so a corrupt download is rejected
//! before it reaches AES-GCM at all.

use std::path::{Component, Path, PathBuf};

use mlib_spec::package::{LatestPointer, PointerError, associated_data, pointer_is_readable};
use sha2::{Digest, Sha256};
use thiserror::Error;

use super::cipher::{self, EncryptError};

/// Schema versions this build's catalog code can read.
const SUPPORTED_SCHEMA: &[i64] = &[mlib_spec::schema::SCHEMA_VERSION];

#[derive(Debug, Error)]
pub enum PackageError {
    #[error("pointer is not readable by this build: {0}")]
    Pointer(#[from] PointerError),
    #[error("downloaded package does not match the pointer's digest")]
    DigestMismatch,
    #[error("package failed to decrypt: {0}")]
    Cipher(#[from] EncryptError),
    #[error("package archive is malformed: {0}")]
    Archive(String),
    #[error("writing the extracted index failed: {0}")]
    Io(String),
}

/// Verifies, decrypts and unpacks a sealed package, returning the path of
/// the `library.db` it extracted into `into`.
pub fn read_package(
    pointer: &LatestPointer,
    sealed: &[u8],
    key: &[u8; 32],
    into: &Path,
) -> Result<PathBuf, PackageError> {
    pointer_is_readable(pointer, SUPPORTED_SCHEMA)?;

    // Verified ahead of the cipher: a truncated or bit-flipped download is
    // then rejected by a cheap digest check rather than by AES-GCM.
    let digest = hex::encode(Sha256::digest(sealed));
    if digest != pointer.sha256 {
        return Err(PackageError::DigestMismatch);
    }

    let aad = associated_data(pointer);
    let plaintext = cipher::open(key, sealed, &aad)?;

    std::fs::create_dir_all(into).map_err(|e| PackageError::Io(e.to_string()))?;
    unpack(&plaintext, into)?;

    let db = into.join("library.db");
    if !db.exists() {
        return Err(PackageError::Archive(
            "archive carried no library.db".into(),
        ));
    }
    Ok(db)
}

/// Gunzips and untars `plaintext` into `dest`, refusing any member whose
/// path would land outside it.
fn unpack(plaintext: &[u8], dest: &Path) -> Result<(), PackageError> {
    let decoder = flate2::read::GzDecoder::new(plaintext);
    let mut archive = tar::Archive::new(decoder);
    let entries = archive
        .entries()
        .map_err(|e| PackageError::Archive(e.to_string()))?;
    for entry in entries {
        let mut entry = entry.map_err(|e| PackageError::Archive(e.to_string()))?;
        let path = entry
            .path()
            .map_err(|e| PackageError::Archive(e.to_string()))?
            .to_path_buf();
        if !is_safe(&path) {
            return Err(PackageError::Archive(
                "archive member escapes the target directory".into(),
            ));
        }
        let target = dest.join(&path);
        if let Some(parent) = target.parent() {
            std::fs::create_dir_all(parent).map_err(|e| PackageError::Io(e.to_string()))?;
        }
        let mut out =
            std::fs::File::create(&target).map_err(|e| PackageError::Io(e.to_string()))?;
        std::io::copy(&mut entry, &mut out).map_err(|e| PackageError::Io(e.to_string()))?;
    }
    Ok(())
}

/// Every component must be an ordinary name: no absolute path, no `..`, no
/// root, nothing a tar entry could use to climb out of `dest`.
fn is_safe(path: &Path) -> bool {
    path.components()
        .all(|c| matches!(c, Component::Normal(_)))
}
