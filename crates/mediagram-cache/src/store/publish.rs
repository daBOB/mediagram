//! Publishing a file whole: stage it under a name unique to the caller,
//! then make it visible with a no-overwrite link. Chunk bodies, a set's
//! `total` and the pairing token all go through here, so a racing reader
//! finds each one either absent or complete, never empty or half written,
//! and the loser of a race leaves the winner's bytes untouched.

use std::fs;
use std::io;
use std::path::Path;

/// A name unique enough that two concurrent writers never stage into the
/// same temp file.
pub(crate) fn temp_name() -> String {
    let mut buf = [0u8; 16];
    getrandom::fill(&mut buf).expect("the OS random source is available");
    hex::encode(buf)
}

/// Links the staged `tmp` to `dest` and removes `tmp` either way.
/// `Ok(true)` when this call published `dest`, `Ok(false)` when something
/// was already there.
pub(crate) fn publish_new(tmp: &Path, dest: &Path) -> io::Result<bool> {
    let linked = fs::hard_link(tmp, dest);
    let _ = fs::remove_file(tmp);
    match linked {
        Ok(()) => Ok(true),
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => Ok(false),
        Err(e) => Err(e),
    }
}
