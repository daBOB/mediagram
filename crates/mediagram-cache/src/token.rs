//! The pairing token: 32 random bytes, hex-encoded, created once and read
//! by every write. It never crosses the wire — only a signature over each
//! PUT does, checked in constant time so a mismatch takes no longer to
//! reject than a match takes to accept.

use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::path::Path;

use hmac::{Hmac, KeyInit, Mac};
use sha2::{Digest, Sha256};

type HmacSha256 = Hmac<Sha256>;

/// The header value's scheme: `Authorization: MGC1 <hex>`.
pub const SCHEME: &str = "MGC1";

/// Reads the token at `state_dir/token`, creating it — 32 random bytes,
/// hex-encoded, mode 0600 — if it is not already there. Lives under
/// `state_dir` rather than the cache root, so clearing the cache does not
/// unpair every device.
pub fn ensure(state_dir: &Path) -> io::Result<String> {
    let path = state_dir.join("token");
    match fs::read_to_string(&path) {
        Ok(text) => Ok(text.trim().to_string()),
        Err(e) if e.kind() == io::ErrorKind::NotFound => {
            fs::create_dir_all(state_dir)?;
            let mut buf = [0u8; 32];
            getrandom::fill(&mut buf).expect("the OS random source is available");
            let token = hex::encode(buf);
            write_private(&path, &token)?;
            Ok(token)
        }
        Err(e) => Err(e),
    }
}

#[cfg(unix)]
fn write_private(path: &Path, token: &str) -> io::Result<()> {
    use std::os::unix::fs::OpenOptionsExt;
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .mode(0o600)
        .open(path)?;
    file.write_all(token.as_bytes())
}

#[cfg(not(unix))]
fn write_private(path: &Path, token: &str) -> io::Result<()> {
    let mut file = OpenOptions::new().write(true).create_new(true).open(path)?;
    file.write_all(token.as_bytes())
}

/// The canonical string a PUT signs: method, path, the `X-Set-Total` value
/// and the hex SHA-256 of the body, newline-separated.
fn canonical(method: &str, path: &str, total: u64, body: &[u8]) -> String {
    let body_hash = hex::encode(Sha256::digest(body));
    format!("{method}\n{path}\n{total}\n{body_hash}")
}

/// `HMAC-SHA256(token, canonical(...))`, hex-encoded — the value that goes
/// after `MGC1 ` in the `Authorization` header.
pub fn sign(token: &str, method: &str, path: &str, total: u64, body: &[u8]) -> String {
    let mut mac =
        HmacSha256::new_from_slice(token.as_bytes()).expect("HMAC accepts a key of any length");
    mac.update(canonical(method, path, total, body).as_bytes());
    hex::encode(mac.finalize().into_bytes())
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
    let mut mac =
        HmacSha256::new_from_slice(token.as_bytes()).expect("HMAC accepts a key of any length");
    mac.update(canonical(method, path, total, body).as_bytes());
    mac.verify_slice(&sig).is_ok()
}

#[cfg(test)]
#[path = "token_tests.rs"]
mod tests;
