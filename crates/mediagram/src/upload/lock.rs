//! A fixed number of uploads at a time, across processes: `upload_slots`,
//! one unless configured otherwise.
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
//!
//! Each slot is its own lock file. Slot 0 is `upload.lock`, the file a single
//! slot always used, so an older binary still running beside a newer one
//! shares that slot rather than adding one: the total never exceeds the
//! configured number.

use std::fs::{File, OpenOptions};
use std::os::fd::AsRawFd;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};

/// The lock file, beside the index it guards writes to.
pub fn path_in(data_dir: &Path) -> PathBuf {
    data_dir.join("upload.lock")
}

/// Slot `n`'s lock file: slot 0 is the historic `upload.lock`.
pub fn slot_path(data_dir: &Path, slot: usize) -> PathBuf {
    if slot == 0 {
        path_in(data_dir)
    } else {
        data_dir.join(format!("upload.lock.{slot}"))
    }
}

/// Held for as long as this value lives; released when it drops, and by the
/// kernel if the process dies without dropping it.
pub struct FileLock {
    _file: File,
}

/// Takes the upload lock, waiting for whoever holds it. `waiting` is called
/// once if there is a wait, so a caller can say so rather than appearing to
/// hang.
pub async fn acquire(data_dir: &Path, waiting: impl FnOnce()) -> Result<FileLock> {
    acquire_file(&path_in(data_dir), waiting).await
}

/// Takes whichever of `slots` upload slots is free first, waiting while all
/// are held. `waiting` is called once if there is a wait.
///
/// With one slot this is [`acquire`] exactly. With more, no single file can
/// be waited on, so the free one is found by trying each in turn every
/// couple of seconds; a wait is for a whole upload, so the poll costs nothing.
pub async fn acquire_slot(
    data_dir: &Path,
    slots: usize,
    waiting: impl FnOnce(),
) -> Result<FileLock> {
    if slots <= 1 {
        return acquire(data_dir, waiting).await;
    }
    let mut waiting = Some(waiting);
    loop {
        for slot in 0..slots {
            let file = open(&slot_path(data_dir, slot))?;
            if try_lock(&file)? {
                return Ok(FileLock { _file: file });
            }
        }
        if let Some(say) = waiting.take() {
            say();
        }
        tokio::time::sleep(std::time::Duration::from_secs(2)).await;
    }
}

/// Whether any of `slots` slots is held right now: something is uploading.
pub fn any_held(data_dir: &Path, slots: usize) -> bool {
    (0..slots.max(1)).any(|slot| {
        open(&slot_path(data_dir, slot)).is_ok_and(|file| !matches!(try_lock(&file), Ok(true)))
    })
}

/// Whether every one of `slots` slots is held right now, so a new upload
/// would queue. A hint, like [`is_held`].
pub fn all_held(data_dir: &Path, slots: usize) -> bool {
    (0..slots.max(1)).all(|slot| {
        open(&slot_path(data_dir, slot)).is_ok_and(|file| !matches!(try_lock(&file), Ok(true)))
    })
}

/// The same kind of lock on any file: one holder across processes, waiting
/// for the current one. `channel_index` takes its publish lock this way.
pub async fn acquire_file(path: &Path, waiting: impl FnOnce()) -> Result<FileLock> {
    let file = open(path)?;
    if try_lock(&file)? {
        return Ok(FileLock { _file: file });
    }
    waiting();
    // Blocking, on a thread of its own: this waits for another process to
    // finish what it holds the lock for (an upload can take an hour), and
    // the runtime has work of its own to keep serving in the meantime.
    let file = tokio::task::spawn_blocking(move || -> Result<File> {
        lock_blocking(&file)?;
        Ok(file)
    })
    .await
    .with_context(|| format!("waiting for {}", path.display()))??;
    Ok(FileLock { _file: file })
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
        crate::paths::ensure_private_dir(dir)?;
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
        _ => Err(anyhow::Error::new(err).context("taking a file lock")),
    }
}

fn lock_blocking(file: &File) -> Result<()> {
    // SAFETY: as above; the file outlives the call.
    match unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX) } {
        0 => Ok(()),
        _ => {
            Err(anyhow::Error::new(std::io::Error::last_os_error())
                .context("waiting for a file lock"))
        }
    }
}

#[cfg(test)]
#[path = "lock_tests.rs"]
mod tests;
