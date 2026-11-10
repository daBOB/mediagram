//! The MGC1 write signature shared by every client of the home cache
//! server: the server verifies it, the player and the uploader produce it.

use hmac::{Hmac, KeyInit, Mac};
use sha2::{Digest, Sha256};

type HmacSha256 = Hmac<Sha256>;

/// The header value's scheme: `Authorization: MGC1 <hex>`.
pub const SCHEME: &str = "MGC1";

/// The canonical string a PUT signs: `"{method}\n{path}\n{total}\n" +
/// hex(sha256(body))`, with no trailing newline after the body hash.
///
/// `total` is the `u64`'s `Display` output (no leading zeros, no `+`), the
/// same spelling a client's own `Long.toString()` produces.
fn mac(token: &str, method: &str, path: &str, total: u64, body: &[u8]) -> HmacSha256 {
    let body_hash = hex::encode(Sha256::digest(body));
    // The key is the token's 64 ASCII hex characters as printed, not the 32
    // bytes they decode to: a client that decodes first signs wrongly.
    let mut mac =
        HmacSha256::new_from_slice(token.as_bytes()).expect("HMAC accepts a key of any length");
    mac.update(format!("{method}\n{path}\n{total}\n{body_hash}").as_bytes());
    mac
}

/// `HMAC-SHA256(token, canonical)`, lowercase hex — the value after `MGC1 `.
pub fn sign(token: &str, method: &str, path: &str, total: u64, body: &[u8]) -> String {
    hex::encode(
        mac(token, method, path, total, body)
            .finalize()
            .into_bytes(),
    )
}

/// Recomputes the signature and compares it to `sig_hex` in constant time.
/// Malformed hex is rejected the same as a mismatch, never a panic.
pub fn verify(
    token: &str,
    sig_hex: &str,
    method: &str,
    path: &str,
    total: u64,
    body: &[u8],
) -> bool {
    let Ok(sig) = hex::decode(sig_hex) else {
        return false;
    };
    mac(token, method, path, total, body)
        .verify_slice(&sig)
        .is_ok()
}

#[cfg(test)]
#[path = "lan_signature_tests.rs"]
mod tests;
