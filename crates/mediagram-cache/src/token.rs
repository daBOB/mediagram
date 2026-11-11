//! The pairing token: 32 random bytes, hex-encoded, created once and read
//! by every write. It never crosses the wire — only a signature over each
//! PUT does, checked in constant time so a mismatch takes no longer to
//! reject than a match takes to accept.

use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::path::Path;

use crate::store::publish::{publish_new, temp_name};

/// Reads the token at `state_dir/token`, creating it — 32 random bytes,
/// hex-encoded, mode 0600 — if it is not already there. Lives under
/// `state_dir` rather than the cache root, so clearing the cache does not
/// unpair every device.
///
/// Refuses to start rather than run with anything short of a well-formed
/// token: a partial write a crash or a full disk left behind would
/// otherwise become a real, if empty or truncated, HMAC key. So the token
/// is written whole to a temp file and only then published under its name,
/// never written in place.
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

    let tmp_path = state_dir.join(temp_name());
    write_private(&tmp_path, &token)?;
    if publish_new(&tmp_path, &path)? {
        return Ok(token);
    }
    read_valid(&path)?.ok_or_else(|| invalid_token_error(&path))
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

pub use mlib_spec::lan_signature::{SCHEME, sign, verify};

#[cfg(test)]
#[path = "token_tests.rs"]
mod tests;
