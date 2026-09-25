//! Pairing a set's total: the first PUT records it, a later one checks
//! against it.

use std::fs;
use std::io::{self, Write};
use std::path::Path;

/// Records `total` at `path` with `create_new`, so two concurrent first
/// writes race on the filesystem rather than on a lock. Returns `None` when
/// this call was the one that created it, or `Some(held)` — what was
/// already recorded — when it lost the race or a later PUT arrived.
pub(super) fn pair(path: &Path, total: u64) -> io::Result<Option<u64>> {
    match fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)
    {
        Ok(mut f) => {
            f.write_all(total.to_string().as_bytes())?;
            Ok(None)
        }
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => {
            let text = fs::read_to_string(path)?;
            let held = text
                .trim()
                .parse::<u64>()
                .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "corrupt total file"))?;
            Ok(Some(held))
        }
        Err(e) => Err(e),
    }
}
