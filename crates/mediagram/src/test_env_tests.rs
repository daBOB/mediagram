//! Which variables the environment guard hides from a test.

use super::hidden;

#[test]
fn the_guard_hides_the_uploader_settings() {
    assert!(hidden("MEDIAGRAM_API_HASH"));
    assert!(hidden("MEDIAGRAM_TMDB_KEY"));
    assert!(!hidden("HOME"));
}

/// ffmpeg tests read the opt-out without the lock, beside tests holding the
/// guard; hiding it made one panic for want of ffmpeg instead of skipping.
#[test]
fn the_ffmpeg_opt_out_stays_visible() {
    assert!(!hidden("MEDIAGRAM_SKIP_FFMPEG_TESTS"));
}
