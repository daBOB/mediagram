//! The pointer, drafted before the archive is encrypted.
//!
//! Its identifying fields are what the cipher authenticates, and none of them
//! depend on the ciphertext, so the draft can be built first and the download
//! fields filled in afterwards. That ordering is why `file`, `url`, `bytes`
//! and `sha256` are outside the authenticated set: `sha256` is the digest of
//! the very bytes the authentication produces.

use mlib_spec::package::{CIPHER, LatestPointer, PACKAGE_FORMAT, key_id};
use sha2::{Digest, Sha256};

/// A pointer carrying only the authenticated fields. Phase 3 completes it
/// once the encrypted file exists.
pub fn draft(created_at: i64, key: &[u8; 32]) -> LatestPointer {
    LatestPointer {
        format: PACKAGE_FORMAT,
        created_at,
        file: String::new(),
        url: String::new(),
        bytes: 0,
        sha256: String::new(),
        cipher: CIPHER.to_string(),
        key_id: key_id(key),
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
    }
}

pub fn sha256(bytes: &[u8]) -> [u8; 32] {
    Sha256::digest(bytes).into()
}
