//! Naming the backup a merge takes before it writes.

use std::path::Path;

/// Where this merge's backup goes. Named to the minute and the process, and
/// never replaced: `sync-index` pulls twice in one process, often within one
/// minute, and the second pull must not overwrite the first one's rollback
/// point, so it takes the next free `-2`, `-3`… instead of failing.
pub(super) fn free_backup_path(data_dir: &Path, stamp: &str) -> std::path::PathBuf {
    let base = format!(
        "library.before-channel-merge-{stamp}-{}",
        std::process::id()
    );
    let mut path = data_dir.join(format!("{base}.db"));
    let mut n = 2;
    while path.exists() {
        path = data_dir.join(format!("{base}-{n}.db"));
        n += 1;
    }
    path
}

#[cfg(test)]
mod tests {
    use super::free_backup_path;

    #[test]
    fn a_second_merge_in_the_same_minute_keeps_the_first_backup() {
        let dir = tempfile::tempdir().unwrap();
        let first = free_backup_path(dir.path(), "260926-1807");
        std::fs::write(&first, b"first").unwrap();
        let second = free_backup_path(dir.path(), "260926-1807");
        assert_ne!(first, second);
        assert!(second.to_string_lossy().ends_with("-2.db"));
        assert_eq!(std::fs::read(&first).unwrap(), b"first");
    }
}
