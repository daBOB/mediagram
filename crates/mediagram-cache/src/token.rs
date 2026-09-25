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
///
/// Refuses to start rather than run with anything short of a well-formed
/// token: a partial write a crash or a full disk left behind (`create_new`
/// then `write_all`, non-atomically, was the earlier design) would
/// otherwise become a real, if empty or truncated, HMAC key.
pub fn ensure(state_dir: &Path) -> io::Result<String> {
    let path = state_dir.join("token");
    if let Some(token) = read_valid(&path)? {
        return Ok(token);
    }
    fs::create_dir_all(state_dir)?;
    // Re-check now the directory certainly exists, in case another process
    // created the token between the check above and here.
    if let Some(token) = read_valid(&path)? {
        return Ok(token);
    }

    let mut buf = [0u8; 32];
    getrandom::fill(&mut buf).expect("the OS random source is available");
    let token = hex::encode(buf);

    // Staged under a name unique to this call, then published with a
    // no-overwrite link — the same publish scheme a chunk body and a set's
    // total use — so the file that ends up at `path` is always either
    // absent or complete, never briefly empty for a racing reader to see.
    let tmp_path = state_dir.join(temp_name());
    write_private(&tmp_path, &token)?;
    let publish = fs::hard_link(&tmp_path, &path);
    let _ = fs::remove_file(&tmp_path);
    match publish {
        Ok(()) => Ok(token),
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => {
            read_valid(&path)?.ok_or_else(|| invalid_token_error(&path))
        }
        Err(e) => Err(e),
    }
}

/// `Ok(None)` for "no token file yet"; `Err` for one that exists but is not
/// exactly 64 lowercase hex characters, which is refused rather than
/// silently accepted as a (weak, or entirely absent) HMAC key.
fn read_valid(path: &Path) -> io::Result<Option<String>> {
    match fs::read_to_string(path) {
        Ok(text) => {
            let token = text.trim().to_string();
            if is_valid_token(&token) {
                Ok(Some(token))
            } else {
                Err(invalid_token_error(path))
            }
        }
        Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e),
    }
}

fn invalid_token_error(path: &Path) -> io::Error {
    io::Error::new(
        io::ErrorKind::InvalidData,
        format!(
            "{} does not hold a 64-character lowercase hex pairing token",
            path.display()
        ),
    )
}

fn is_valid_token(token: &str) -> bool {
    token.len() == 64
        && token
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}

fn temp_name() -> String {
    let mut buf = [0u8; 16];
    getrandom::fill(&mut buf).expect("the OS random source is available");
    hex::encode(buf)
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

/// The canonical string a PUT signs: `"{method}\n{path}\n{total}\n" +
/// hex(sha256(body))`, with no trailing newline after the body hash.
///
/// `path` and `total` are their canonical decimal spelling — `total` is
/// this `u64`'s `Display` output (no leading zeros, no `+` sign, matching
/// what a client's own `Long.toString()` produces), and `path` is rebuilt
/// from the validated id and the parsed chunk number
/// ([`crate::http`]'s `put_chunk`), not copied from the raw request text.
fn canonical(method: &str, path: &str, total: u64, body: &[u8]) -> String {
    let body_hash = hex::encode(Sha256::digest(body));
    format!("{method}\n{path}\n{total}\n{body_hash}")
}

/// `HMAC-SHA256(token, canonical(...))`, hex-encoded — the value that goes
/// after `MGC1 ` in the `Authorization` header.
///
/// The HMAC key is `token.as_bytes()`: the 64 ASCII bytes of the hex string
/// itself, exactly as printed by `mediagram_cache token` and typed into a
/// pairing device — **not** the 32 bytes that hex decodes to. An
/// implementation that decodes the token first will compute a different,
/// silently wrong, signature.
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
