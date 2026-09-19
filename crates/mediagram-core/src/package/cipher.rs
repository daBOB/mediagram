//! The package cipher.
//!
//! AES-256-GCM over the whole archive, with the pointer's identifying fields
//! passed as associated data. A reader rebuilds that data from the pointer it
//! fetched, so an archive replayed under an edited pointer fails its tag
//! rather than being accepted as the current package.
//!
//! The file is `nonce (12 bytes) || ciphertext || tag (16 bytes)`. Whole-file
//! rather than streamed, deliberately: a reader must verify the tag before it
//! trusts any plaintext, and a single-shot API is the one shape that cannot
//! be misused into handing out unverified bytes.

use aes_gcm::aead::{Aead, KeyInit, Payload};
use aes_gcm::{Aes256Gcm, Key, Nonce};
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use thiserror::Error;

/// Bytes of the random nonce prefixed to every package.
pub const NONCE_LEN: usize = 12;
/// Bytes of the authentication tag GCM appends.
pub const TAG_LEN: usize = 16;

#[derive(Error, Debug, PartialEq, Eq)]
pub enum EncryptError {
    #[error("package key is not valid base64")]
    KeyEncoding,
    #[error("package key decodes to {0} bytes, expected 32")]
    KeyLength(usize),
    #[error("package is too short to contain a nonce")]
    TooShort,
    #[error("package failed authentication: wrong key, corrupt file, or edited pointer")]
    Tag,
    #[error("package encryption failed")]
    Cipher,
}

/// Decodes a configured key. Errors never quote the input: a malformed key is
/// still key material.
pub fn parse_key(encoded: &str) -> Result<[u8; 32], EncryptError> {
    let bytes = STANDARD
        .decode(encoded.trim())
        .map_err(|_| EncryptError::KeyEncoding)?;
    let len = bytes.len();
    bytes.try_into().map_err(|_| EncryptError::KeyLength(len))
}

/// Encrypts `plaintext` with a fresh nonce, returning `nonce || ciphertext || tag`.
pub fn seal(key: &[u8; 32], plaintext: &[u8], aad: &[u8]) -> Result<Vec<u8>, EncryptError> {
    let mut nonce_bytes = [0u8; NONCE_LEN];
    // Straight from the OS generator: a nonce repeated under one key exposes
    // GCM's authentication key, so this must never come from a seeded source.
    getrandom::fill(&mut nonce_bytes).map_err(|_| EncryptError::Cipher)?;

    let cipher = Aes256Gcm::new(&Key::<Aes256Gcm>::from(*key));
    let nonce = Nonce::from(nonce_bytes);
    let ciphertext = cipher
        .encrypt(
            &nonce,
            Payload {
                msg: plaintext,
                aad,
            },
        )
        .map_err(|_| EncryptError::Cipher)?;

    let mut out = Vec::with_capacity(NONCE_LEN + ciphertext.len());
    out.extend_from_slice(&nonce_bytes);
    out.extend_from_slice(&ciphertext);
    Ok(out)
}

/// Verifies and decrypts a package. Returns plaintext only after the tag
/// checks out, so a caller can never act on unauthenticated bytes.
pub fn open(key: &[u8; 32], sealed: &[u8], aad: &[u8]) -> Result<Vec<u8>, EncryptError> {
    if sealed.len() < NONCE_LEN + TAG_LEN {
        return Err(EncryptError::TooShort);
    }
    let (nonce_bytes, body) = sealed.split_at(NONCE_LEN);
    let nonce = Nonce::try_from(nonce_bytes).map_err(|_| EncryptError::TooShort)?;
    let cipher = Aes256Gcm::new(&Key::<Aes256Gcm>::from(*key));
    cipher
        .decrypt(&nonce, Payload { msg: body, aad })
        .map_err(|_| EncryptError::Tag)
}
