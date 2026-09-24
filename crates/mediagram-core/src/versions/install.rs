//! Making an assembled catalog the current one.

use std::path::{Path, PathBuf};

use tokio::sync::{Mutex, MutexGuard};

use super::CURRENT;
use crate::error::CoreError;

const INCOMING: &str = "incoming";

/// The directory the next version is assembled in, held by one install at a
/// time.
///
/// There is one such directory, and clearing it is the first thing an
/// install does — and the last, when the swap removes every leftover. Two
/// installs sharing it would each delete the other's half-written catalog,
/// so holding a `Staging` is holding the turn to install.
pub struct Staging<'a> {
    root: PathBuf,
    dir: PathBuf,
    _turn: MutexGuard<'a, ()>,
}

impl<'a> Staging<'a> {
    /// Waits for the turn, then hands back an empty staging directory.
    pub async fn begin(turn: &'a Mutex<()>, root: &Path) -> Result<Staging<'a>, CoreError> {
        let held = turn.lock().await;
        let dir = root.join(INCOMING);
        if let Err(error) = std::fs::remove_dir_all(&dir) {
            if error.kind() != std::io::ErrorKind::NotFound {
                return Err(CoreError::io(
                    "clearing the refreshed catalog staging directory",
                )(error));
            }
        }
        std::fs::create_dir_all(&dir).map_err(CoreError::io("staging the refreshed catalog"))?;
        Ok(Staging {
            root: root.to_path_buf(),
            dir,
            _turn: held,
        })
    }

    pub fn dir(&self) -> &Path {
        &self.dir
    }

    /// Installs what was assembled as `version_name`, returning the name it
    /// was installed under, and gives up the turn.
    pub fn install(self, version_name: &str) -> Result<String, CoreError> {
        install_staged(&self.root, &self.dir, version_name)
    }
}

/// Moves a staged catalog into place under `version_name` and points
/// `current` at it, clearing every version the swap leaves behind. Returns
/// the name it was installed under.
///
/// The one way a catalog becomes the current one, whichever path assembled
/// it: the atomicity below is the reason a reader that dies mid-refresh sees
/// one whole catalog or the other. Callers hold a [`Staging`]; a test may
/// stage by hand.
pub fn install_staged(
    root: &Path,
    incoming: &Path,
    version_name: &str,
) -> Result<String, CoreError> {
    let installed = free_version_name(root, version_name);
    std::fs::rename(incoming, root.join(&installed))
        .map_err(CoreError::io("staging the refreshed catalog"))?;
    swap_current(root, &installed)?;
    remove_other_versions(root, &installed);
    Ok(installed)
}

/// `version_name`, or the first `version_name-<n>` not already taken.
///
/// Never an existing directory: the one `current` points at is the likeliest
/// to share the name — a refresh that finds nothing newer installs the same
/// version again — and clearing it before the rename would leave `current`
/// dangling until the swap, and for good if the rename failed. The old copy
/// goes afterwards, with every other stale version.
fn free_version_name(root: &Path, version_name: &str) -> String {
    if !root.join(version_name).exists() {
        return version_name.to_string();
    }
    (1u32..)
        .map(|n| format!("{version_name}-{n}"))
        .find(|name| !root.join(name).exists())
        .expect("some suffix is free")
}

/// Points `current` at `version_name`, atomically: a symlink renamed over
/// another is a single filesystem operation, so a reader that dies mid
/// refresh is looking at one whole catalog or the other, never at half of
/// each, and a handle already open keeps the version it opened.
fn swap_current(root: &Path, version_name: &str) -> Result<(), CoreError> {
    let staged = root.join(".current-swap");
    let _ = std::fs::remove_file(&staged);
    std::os::unix::fs::symlink(version_name, &staged)
        .map_err(CoreError::io("staging the catalog switch"))?;
    std::fs::rename(&staged, root.join(CURRENT))
        .map_err(CoreError::io("switching the current catalog"))
}

/// Clears every stale version and leftover staging directory, keeping only
/// the one just published and whatever `current` points at.
fn remove_other_versions(root: &Path, keep: &str) {
    let Ok(entries) = std::fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(name) = name.to_str() else { continue };
        if name == keep || name == CURRENT {
            continue;
        }
        if name.starts_with("v-") || name == INCOMING {
            let _ = std::fs::remove_dir_all(entry.path());
        }
    }
}

#[cfg(test)]
mod tests {
    use std::future::Future;
    use std::os::unix::fs::PermissionsExt;
    use std::task::{Context, Waker};

    use super::*;

    #[tokio::test]
    async fn a_staging_directory_that_cannot_be_cleared_is_refused() {
        let root = tempfile::tempdir().unwrap();
        let incoming = root.path().join(INCOMING);
        std::fs::create_dir(&incoming).unwrap();
        std::fs::write(incoming.join("stale-catalog"), b"old").unwrap();
        std::fs::set_permissions(&incoming, std::fs::Permissions::from_mode(0o500)).unwrap();
        // Privileged users can bypass mode bits, so this fixture cannot provoke
        // a cleanup error there. Restore permissions before dropping the fixture.
        if std::fs::write(incoming.join("permission-probe"), b"").is_ok() {
            std::fs::set_permissions(&incoming, std::fs::Permissions::from_mode(0o700)).unwrap();
            eprintln!("permission-based cleanup failure requires an unprivileged user");
            return;
        }
        let turn = Mutex::new(());

        let result = Staging::begin(&turn, root.path()).await;

        // Restore permissions before asserting so the fixture always cleans up.
        std::fs::set_permissions(&incoming, std::fs::Permissions::from_mode(0o700)).unwrap();
        assert!(
            result.is_err(),
            "an uncleared staging directory must not be reused"
        );
        assert_eq!(
            std::fs::read(incoming.join("stale-catalog")).unwrap(),
            b"old"
        );
    }

    /// Two refreshes overlapping must take turns: the second may not clear
    /// the staging directory while the first is still assembling in it.
    #[tokio::test]
    async fn a_second_install_waits_for_the_first_to_finish() {
        let root = tempfile::tempdir().unwrap();
        let turn = Mutex::new(());
        let first = Staging::begin(&turn, root.path()).await.unwrap();
        std::fs::write(first.dir().join("half-written"), b"x").unwrap();

        let mut second = std::pin::pin!(Staging::begin(&turn, root.path()));
        assert!(
            second
                .as_mut()
                .poll(&mut Context::from_waker(Waker::noop()))
                .is_pending(),
            "the second install began while the first held the turn"
        );
        assert!(first.dir().join("half-written").exists());

        first.install("v-1").unwrap();
        let second = second.await.unwrap();
        assert!(second.dir().read_dir().unwrap().next().is_none());
        assert!(root.path().join("v-1").join("half-written").exists());
    }
}
