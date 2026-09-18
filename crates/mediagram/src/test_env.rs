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

/// Serializes every test that reads or writes the process environment.
static ENV_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

/// Holds [`ENV_LOCK`] and hides every `MEDIAGRAM_*` variable for the duration,
/// putting them back when the test ends. A test that wants an override sets it
/// itself after taking the guard.
pub(crate) struct EnvGuard {
    _lock: std::sync::MutexGuard<'static, ()>,
    saved: Vec<(String, String)>,
}

impl EnvGuard {
    pub(crate) fn new() -> Self {
        let lock = ENV_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let saved: Vec<(String, String)> = std::env::vars()
            .filter(|(k, _)| k.starts_with("MEDIAGRAM_"))
            .collect();
        for (k, _) in &saved {
            unsafe { std::env::remove_var(k) };
        }
        Self { _lock: lock, saved }
    }
}

impl Drop for EnvGuard {
    fn drop(&mut self) {
        for (k, v) in &self.saved {
            unsafe { std::env::set_var(k, v) };
        }
    }
}
