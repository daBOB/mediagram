//! Environment isolation for tests that call [`crate::config::load`].
//!
//! `load` overlays `MEDIAGRAM_*` variables onto the file it read, and applies
//! them before it validates. A developer who has exported a real api_hash to
//! run the uploader therefore refills the field a test blanked out on purpose,
//! and assertions end up reading the shell instead of the file under test.
//!
//! One lock for the whole library: unit tests across modules run as threads in
//! a single process, so a mutex per module would not serialize them against
//! each other. The integration tests keep their own copy, because an
//! integration test is a separate crate and sharing this would mean exporting
//! test scaffolding from the public API.

/// Serializes every test that writes the process environment or reads a
/// variable [`EnvGuard`] hides.
static ENV_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

/// Holds [`ENV_LOCK`] and hides the `MEDIAGRAM_*` variables for the duration,
/// putting them back when the test ends. A test that wants an override sets it
/// itself after taking the guard.
///
/// `MEDIAGRAM_SKIP_FFMPEG_TESTS` is left alone: the ffmpeg fixtures read it
/// without the lock, from tests running beside these, and an ffmpeg test that
/// found it hidden would fail for want of ffmpeg instead of skipping.
pub(crate) struct EnvGuard {
    _lock: std::sync::MutexGuard<'static, ()>,
    saved: Vec<(String, String)>,
}

impl EnvGuard {
    pub(crate) fn new() -> Self {
        let lock = ENV_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let saved: Vec<(String, String)> = std::env::vars().filter(|(k, _)| hidden(k)).collect();
        for (k, _) in &saved {
            // SAFETY: every test that writes the environment, and every test
            // that reads a variable removed here, holds ENV_LOCK, taken above.
            // The one unlocked reader, of MEDIAGRAM_SKIP_FFMPEG_TESTS, goes
            // through std::env, which serializes against this removal, and
            // never finds its variable gone because it is not one of these.
            unsafe { std::env::remove_var(k) };
        }
        Self { _lock: lock, saved }
    }
}

/// Whether the guard hides `key`. Every `MEDIAGRAM_*` variable, not a list of
/// the ones `load` reads: a list goes stale the day `load` learns a new one,
/// and the developer's shell leaks into the tests again.
fn hidden(key: &str) -> bool {
    key.starts_with("MEDIAGRAM_") && key != "MEDIAGRAM_SKIP_FFMPEG_TESTS"
}

impl Drop for EnvGuard {
    fn drop(&mut self) {
        for (k, v) in &self.saved {
            // SAFETY: ENV_LOCK is still held; it is released after this
            // loop, when the guard's lock field drops.
            unsafe { std::env::set_var(k, v) };
        }
    }
}

#[path = "test_env_tests.rs"]
mod tests;
