//! The prebuilt metadata package: the manifest carried inside the encrypted
//! archive, the plaintext pointer published beside it, and the rule binding
//! the two together.
//!
//! The pointer is published in the clear and anyone can rewrite it, so its
//! identifying fields are fed to the cipher as associated data. A replayed
//! archive offered under an edited pointer then fails its authentication tag
//! instead of being accepted as current.

mod charset;
pub mod naming;

use charset::{is_digits, is_lower_alpha, is_lower_hex};

pub use naming::package_file_name;

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use thiserror::Error;

/// Package layout version, distinct from the caption spec version and from
/// the `library.db` schema version.
pub const PACKAGE_FORMAT: u32 = 1;

/// The only cipher this format version defines.
pub const CIPHER: &str = "aes-256-gcm";

pub(crate) const FILE_PREFIX: &str = "prebuilt_mediagram_db";
pub(crate) const FILE_SUFFIX: &str = ".tar.gz.enc";
/// Digest bytes used to make a file name unique, rendered as twice as many
/// hex characters.
pub(crate) const NAME_HASH_BYTES: usize = 4;
/// Hex length of `key_id`, and of a full sha256, as they appear in a pointer.
const KEY_ID_HEX: usize = NAME_HASH_BYTES * 2;
const SHA256_HEX: usize = 64;
/// A reader verifies the tag over the whole file before it sees any
/// plaintext, so a package has to stay within a modest device's memory.
pub const MAX_PACKAGE_BYTES: u64 = 64 * 1024 * 1024;

/// One poster inside the archive. Field order is wire order.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct PosterEntry {
    pub key: String,
    pub file: String,
}

/// Describes the archive's contents. Lives inside the ciphertext, so it may
/// carry counts; nothing here is published in the clear.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct PackageManifest {
    pub format: u32,
    pub created_at: i64,
    pub schema: i64,
    pub spec: u32,
    pub sets: u64,
    pub parts: u64,
    pub posters: Vec<PosterEntry>,
}

/// The pointer published at a fixed URL. Deliberately says nothing about the
/// library: no titles, no counts, no chat or message identifiers.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct LatestPointer {
    pub format: u32,
    pub created_at: i64,
    pub file: String,
    pub url: String,
    pub bytes: u64,
    pub sha256: String,
    pub cipher: String,
    pub key_id: String,
    pub schema: i64,
    pub spec: u32,
}

/// The identifying fields, in the order they are authenticated.
#[derive(Serialize)]
struct AssociatedData<'a> {
    format: u32,
    created_at: i64,
    key_id: &'a str,
    schema: i64,
    spec: u32,
}

/// Bytes authenticated alongside the ciphertext. A reader rebuilds these from
/// the pointer it fetched; if any of the five fields was altered in transit,
/// decryption fails rather than yielding a stale package presented as fresh.
///
/// The download fields (`file`, `url`, `bytes`, `sha256`) are excluded of
/// necessity, not preference: `sha256` is the digest of the very ciphertext
/// this data helps produce, so authenticating it would be circular, and the
/// other three follow it. A reader must therefore treat those four as
/// unauthenticated hints, useful only for fetching and for rejecting a
/// corrupt download. In particular a reader must not decide "I already have
/// this package" from `sha256`, because an attacker can set it to the digest
/// of the copy the reader already holds and suppress an update without ever
/// invoking the cipher.
pub fn associated_data(pointer: &LatestPointer) -> Vec<u8> {
    let identifying = AssociatedData {
        format: pointer.format,
        created_at: pointer.created_at,
        key_id: &pointer.key_id,
        schema: pointer.schema,
        spec: pointer.spec,
    };
    // Field order is fixed by the struct, so this serialization is canonical.
    serde_json::to_vec(&identifying).expect("associated data is plain scalars")
}

/// A short, public fingerprint of the package key, so a reader holding the
/// wrong key can stop before downloading the archive. Not a security control:
/// it only distinguishes keys, it never proves possession of one.
pub fn key_id(key: &[u8; 32]) -> String {
    let digest = Sha256::digest(key);
    hex::encode(&digest[..4])
}

/// A poster key reaches a file name, a manifest path and a tar member name,
/// so it is restricted to `source-kind-digits` before it touches any path.
///
/// A season's artwork adds one more part, `s<digits>` — `tmdb-tv-1396-s2` —
/// so it sits beside its show's poster under the same rules and is never
/// mistaken for a different show: the show's own key has no fourth part.
pub fn poster_key_is_valid(key: &str) -> bool {
    let mut parts = key.split('-');
    let (Some(source), Some(kind), Some(id)) = (parts.next(), parts.next(), parts.next()) else {
        return false;
    };
    let season_ok = match (parts.next(), parts.next()) {
        (None, _) => true,
        (Some(season), None) => season.strip_prefix('s').is_some_and(is_digits),
        _ => false,
    };
    is_lower_alpha(source) && is_lower_alpha(kind) && is_digits(id) && season_ok
}

/// The key a season's artwork is stored under, beside its show's `show_key`.
pub fn season_poster_key(show_key: &str, season: u32) -> String {
    format!("{show_key}-s{season}")
}

#[derive(Error, Debug, PartialEq, Eq)]
pub enum PointerError {
    #[error("package format {0} is newer than this reader understands")]
    UnsupportedFormat(u32),
    #[error("package holds schema {0}, which this reader does not support")]
    UnsupportedSchema(i64),
    #[error("package cipher `{0}` is not recognised")]
    UnsupportedCipher(String),
    #[error("pointer field `{0}` is malformed")]
    Malformed(&'static str),
    #[error("pointer claims {0} bytes, over the {MAX_PACKAGE_BYTES} byte limit")]
    TooLarge(u64),
}

/// Whether a reader can use the package a pointer names, decided before any
/// download. `supported_schema` is the set of `library.db` layouts the caller
/// can read.
pub fn pointer_is_readable(
    pointer: &LatestPointer,
    supported_schema: &[i64],
) -> Result<(), PointerError> {
    if pointer.format != PACKAGE_FORMAT {
        return Err(PointerError::UnsupportedFormat(pointer.format));
    }
    if pointer.cipher != CIPHER {
        return Err(PointerError::UnsupportedCipher(pointer.cipher.clone()));
    }
    if !supported_schema.contains(&pointer.schema) {
        return Err(PointerError::UnsupportedSchema(pointer.schema));
    }
    // Everything below arrives in a plaintext file from a public URL, so it
    // is checked before it reaches a cipher, a file name or an allocation.
    if !is_lower_hex(&pointer.key_id, KEY_ID_HEX) {
        return Err(PointerError::Malformed("key_id"));
    }
    if !is_lower_hex(&pointer.sha256, SHA256_HEX) {
        return Err(PointerError::Malformed("sha256"));
    }
    if pointer.created_at < 0 {
        return Err(PointerError::Malformed("created_at"));
    }
    if pointer.bytes == 0 {
        return Err(PointerError::Malformed("bytes"));
    }
    if pointer.bytes > MAX_PACKAGE_BYTES {
        return Err(PointerError::TooLarge(pointer.bytes));
    }
    Ok(())
}
