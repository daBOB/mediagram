//! Deleting the source the person asked to have deleted, once its set is
//! complete.

use std::path::Path;

/// Removes the file the person named, once the index says every part of it
/// is in the channel. The parts are not read back; that is what `verify` is
/// for, and what someone whose local copy is the only other one runs first.
pub(super) fn report_deletion(path: &Path, complete: bool, total: u64) {
    let name = path
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| path.display().to_string());
    if !complete {
        println!("  {name} kept: not every part reached the channel");
        return;
    }
    match std::fs::remove_file(path) {
        Ok(()) => println!("  {name} deleted, {:.2} GB freed", total as f64 / 1e9),
        Err(err) => println!("  {name} kept: {err}"),
    }
}

#[cfg(test)]
#[path = "delete_source_tests.rs"]
mod tests;
