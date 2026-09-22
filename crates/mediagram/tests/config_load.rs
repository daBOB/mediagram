//! Pins `config::load`'s behavior at its edges: environment overrides that
//! fail to parse or fail validation, a missing config file's error message,
//! and the field values `load` accepts even though they are useless at
//! runtime.

use std::env;
use std::path::Path;

use tempfile::NamedTempFile;

use mediagram::config;

/// Serializes every test in this file: several read or write MEDIAGRAM_*
/// variables through `config::load`, and the process environment is shared
/// across test threads in the same binary.
static ENV_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

/// Holds [`ENV_LOCK`] and hides whatever MEDIAGRAM_* variables the developer
/// happens to have exported, putting them back when the test ends.
///
/// `config::load` consults those variables by design and applies them before
/// it validates, so a shell that has a real api_hash exported in it would
/// satisfy the very field a test blanked out to prove the check fires.
/// Clearing them keeps the file under test the only input. A test that wants
/// an override sets it itself after taking the guard.
///
/// This is a local copy of `crate::test_env::EnvGuard`, not a reuse of it:
/// that helper is `pub(crate)`, so it is invisible from an integration test,
/// which compiles as its own crate against `mediagram`'s public API.
struct EnvGuard {
    _lock: std::sync::MutexGuard<'static, ()>,
    saved: Vec<(String, String)>,
}

impl EnvGuard {
    fn new() -> Self {
        let lock = ENV_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let saved: Vec<(String, String)> = env::vars()
            .filter(|(k, _)| k.starts_with("MEDIAGRAM_"))
            .collect();
        for (k, _) in &saved {
            unsafe { env::remove_var(k) };
        }
        Self { _lock: lock, saved }
    }
}

impl Drop for EnvGuard {
    fn drop(&mut self) {
        for (k, v) in &self.saved {
            unsafe { env::set_var(k, v) };
        }
    }
}

#[test]
fn env_override_with_non_numeric_part_size_fails_to_parse() {
    let _env_guard = EnvGuard::new();
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    unsafe {
        env::set_var("MEDIAGRAM_PART_SIZE", "not_a_number");
    }
    let result = config::load(Some(temp.path()));
    unsafe {
        env::remove_var("MEDIAGRAM_PART_SIZE");
    }

    // A non-numeric MEDIAGRAM_PART_SIZE fails at parse time, before the
    // aligned-to-1-MiB check ever runs.
    assert!(result.is_err());
}

#[test]
fn env_override_with_part_size_not_mib_aligned_is_rejected() {
    let _env_guard = EnvGuard::new();
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    unsafe {
        env::set_var("MEDIAGRAM_PART_SIZE", "1048575"); // 1 MiB - 1
    }
    let result = config::load(Some(temp.path()));
    unsafe {
        env::remove_var("MEDIAGRAM_PART_SIZE");
    }

    // part_size must be a multiple of 1 MiB; the error names the field.
    assert!(result.is_err());
    assert!(
        result
            .unwrap_err()
            .to_string()
            .to_lowercase()
            .contains("part_size")
    );
}

#[test]
fn missing_config_file_error_points_to_example_toml() {
    let _env_guard = EnvGuard::new();
    let result = config::load(Some(Path::new("/nonexistent/fake_config.toml")));
    assert!(result.is_err());
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("config.example.toml"));
}

#[test]
fn empty_api_hash_is_rejected() {
    let _env_guard = EnvGuard::new();
    let toml_content = r#"
api_id = 123456
api_hash = ""
channel = "test_channel"
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    // load() bails when api_hash or channel is empty; this test covers the
    // api_hash branch of that same check.
    let result = config::load(Some(temp.path()));
    assert!(result.is_err());
}

#[test]
fn max_attempts_of_zero_loads_without_complaint() {
    let _env_guard = EnvGuard::new();
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
max_attempts = 0
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    // load() places no lower bound on max_attempts; a value that would make
    // the retry policy give up immediately is still a valid config. (The
    // retry policy itself clamps this at runtime — see telegram::retry.)
    let cfg = config::load(Some(temp.path()));
    assert!(cfg.is_ok());
    assert_eq!(cfg.unwrap().max_attempts, 0);
}

#[test]
fn max_attempts_of_one_loads_without_complaint() {
    let _env_guard = EnvGuard::new();
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
max_attempts = 1
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    let cfg = config::load(Some(temp.path()));
    assert!(cfg.is_ok());
    assert_eq!(cfg.unwrap().max_attempts, 1);
}
