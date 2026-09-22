//! One upload at a time, across processes.
//!
//! `add` hands its bytes to a background process, so two adds a minute apart
//! would otherwise have two uploads competing for the same line — each half
//! as fast, and a channel interleaving parts of unrelated sets. A show is
//! uploaded episode after episode, and this is the same rule for files added
//! one at a time: the second waits for the first.
//!
//! An advisory `flock` on a file in the data directory, held for as long as
//! the uploading process lives. The kernel releases it when that process
//! exits however it exits, which a pid file written by hand does not.

use std::fs::{File, OpenOptions};
use std::os::fd::AsRawFd;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};

/// The lock file, beside the index it guards writes to.
pub fn path_in(data_dir: &Path) -> PathBuf {
    data_dir.join("upload.lock")
}

/// Held for as long as this value lives; released when it drops, and by the
/// kernel if the process dies without dropping it.
pub struct UploadLock {
    _file: File,
}

/// Takes the lock, waiting for whoever holds it. `waiting` is called once if
/// there is a wait, so a caller can say so rather than appearing to hang.
pub async fn acquire(data_dir: &Path, waiting: impl FnOnce()) -> Result<UploadLock> {
    let path = path_in(data_dir);
    let file = open(&path)?;
    if try_lock(&file)? {
        return Ok(UploadLock { _file: file });
    }
    waiting();
    // Blocking, on a thread of its own: this waits for another process to
    // finish uploading, which can be an hour, and the runtime has an upload
    // of its own to keep serving in the meantime.
    let file = tokio::task::spawn_blocking(move || -> Result<File> {
        lock_blocking(&file)?;
        Ok(file)
    })
    .await
    .context("waiting for the upload lock")??;
    Ok(UploadLock { _file: file })
}

/// Whether somebody holds it right now, for a caller that only wants to say
/// what is going on. A hint: it can be true a moment after it was false.
pub fn is_held(data_dir: &Path) -> bool {
    let Ok(file) = open(&path_in(data_dir)) else {
        return false;
    };
    // Taking it means nobody else had it; it is released again on return.
    !matches!(try_lock(&file), Ok(true))
}

fn open(path: &Path) -> Result<File> {
    if let Some(dir) = path.parent() {
        crate::paths::private_dir(dir)?;
    }
    OpenOptions::new()
        .create(true)
        .read(true)
        .write(true)
        .truncate(false)
        .open(path)
        .with_context(|| format!("opening {}", path.display()))
}

/// `true` when the lock was taken, `false` when somebody else holds it.
fn try_lock(file: &File) -> Result<bool> {
    // SAFETY: flock on a file descriptor this process owns and keeps alive
    // for the call.
    let taken = unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) };
    if taken == 0 {
        return Ok(true);
    }
    let err = std::io::Error::last_os_error();
    match err.raw_os_error() {
        Some(libc::EWOULDBLOCK) => Ok(false),
        _ => Err(anyhow::Error::new(err).context("locking the upload lock")),
    }
}

fn lock_blocking(file: &File) -> Result<()> {
    // SAFETY: as above; the file outlives the call.
    match unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX) } {
        0 => Ok(()),
        _ => Err(anyhow::Error::new(std::io::Error::last_os_error())
            .context("waiting for the upload lock")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn a_second_holder_waits_and_is_told_it_is_waiting() {
        let dir = tempfile::tempdir().unwrap();
        let held = acquire(dir.path(), || panic!("nothing else holds it")).await.unwrap();
        assert!(is_held(dir.path()));

        // Nobody can take it while it is held, and letting go frees it.
        let file = open(&path_in(dir.path())).unwrap();
        assert!(!try_lock(&file).unwrap());
        drop(held);
        assert!(!is_held(dir.path()));
        assert!(try_lock(&file).unwrap());
    }

    #[tokio::test]
    async fn waiting_is_only_reported_when_there_is_a_wait() {
        let dir = tempfile::tempdir().unwrap();
        let mut said = false;
        let lock = acquire(dir.path(), || said = true).await.unwrap();
        assert!(!said);
        drop(lock);
    }
}
