//! Pairing a set's total: the first PUT records it, a later one checks
//! against it.
//!
//! `create_new` on the target directly (the earlier design) leaves a window
//! between the file becoming visible and its content being written: a
//! racing reader in that window sees an empty file. Instead the full
//! content is staged in a uniquely named temp file first, and only a
//! `hard_link` — atomic, and either fully there or not there at all —
//! publishes it, the same publish scheme chunk bodies use.

use std::fs;
use std::io;
use std::path::Path;
use std::time::Duration;

use super::temp_name;

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
    let publish = fs::hard_link(&tmp_path, path);
    let _ = fs::remove_file(&tmp_path);

    match publish {
        Ok(()) => Ok(None),
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => {
            // The winner's `hard_link` only ever makes a fully written file
            // visible — there is no partial-content window to lose a race
            // into — so this should resolve on the first read. It retries
            // anyway, briefly, as a defense against a `total` file a
            // pre-fix build left empty or truncated on disk; genuinely
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
        Err(e) => Err(e),
    }
}

/// `Ok(None)` for "not there yet" (the caller should try to become the
/// writer), never an error for empty or unparseable content — only a
/// missing file and a genuinely unreadable one are `Err`/`Ok(None)`
/// respectively; the ambiguous case belongs to the retry loop that calls
/// this, not to a single read.
fn read_valid(path: &Path) -> io::Result<Option<u64>> {
    match fs::read_to_string(path) {
        Ok(text) => Ok(text.trim().parse::<u64>().ok()),
        Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e),
    }
}
