//! Pairing a set's total: the first PUT records it, a later one checks
//! against it.
//!
//! `create_new` on the target directly would leave a window between the
//! file becoming visible and its content being written, in which a racing
//! reader sees an empty file. Instead the full content is staged in a
//! uniquely named temp file first, and only a `hard_link` — atomic, and
//! either fully there or not there at all — publishes it
//! ([`super::publish`]).

use std::fs;
use std::io;
use std::path::Path;
use std::time::Duration;

use super::publish::{publish_new, temp_name};

/// Records `total` at `path`, or reads back what a racing writer recorded
/// there instead. Returns `None` when this call was the one that created
/// it, or `Some(held)` — what was already recorded — when it lost the race
/// or a later PUT arrived.
pub(super) fn pair(tmp_dir: &Path, path: &Path, total: u64) -> io::Result<Option<u64>> {
    if let Some(held) = read_valid(path)? {
        return Ok(Some(held));
    }

    let tmp_path = tmp_dir.join(temp_name());
    fs::write(&tmp_path, total.to_string())?;
    if publish_new(&tmp_path, path)? {
        return Ok(None);
    }
    // The winner's `hard_link` only ever makes a fully written file
    // visible — there is no partial-content window to lose a race
    // into — so this should resolve on the first read. It retries
    // anyway, briefly, because the cache may still hold a `total` file
    // that was written in place and left empty or truncated; genuinely
    // corrupt, unrecovered data still errors once the budget is
    // spent, rather than retrying forever.
    for _ in 0..20 {
        if let Some(held) = read_valid(path)? {
            return Ok(Some(held));
        }
        std::thread::sleep(Duration::from_millis(5));
    }
    Err(io::Error::new(
        io::ErrorKind::InvalidData,
        format!(
            "{} never became a valid total after losing the write race",
            path.display()
        ),
    ))
}

/// `Ok(None)` when the file is missing or its content is empty or
/// unparseable; `Err` only for a real read failure.
pub(super) fn read_valid(path: &Path) -> io::Result<Option<u64>> {
    match fs::read_to_string(path) {
        Ok(text) => Ok(text.trim().parse::<u64>().ok()),
        Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e),
    }
}

#[cfg(test)]
#[path = "total_tests.rs"]
mod tests;
