use std::env;

use tempfile::NamedTempFile;

use super::*;

/// Serializes every test in this file: they read or write
/// `MEDIAGRAM_CACHE_*` variables, and the process environment is shared
/// across test threads in the same binary.
static ENV_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

struct EnvGuard {
    _lock: std::sync::MutexGuard<'static, ()>,
    saved: Vec<(String, String)>,
}

impl EnvGuard {
    fn new() -> Self {
        let lock = ENV_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let saved: Vec<(String, String)> = env::vars()
            .filter(|(k, _)| k.starts_with("MEDIAGRAM_CACHE_"))
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
fn defaults_apply_with_no_file_and_no_env() {
    let _guard = EnvGuard::new();
    let cfg = load(None).unwrap();
    assert_eq!(cfg.listen, DEFAULT_LISTEN);
    assert_eq!(cfg.budget, DEFAULT_BUDGET_BYTES);
    assert!(cfg.mdns);
}

#[test]
fn a_toml_file_overrides_the_defaults() {
    let _guard = EnvGuard::new();
    let file = NamedTempFile::new().unwrap();
    std::fs::write(
        file.path(),
        r#"
        listen = "0.0.0.0:9999"
        budget = 12345
        mdns = false
        "#,
    )
    .unwrap();

    let cfg = load(Some(file.path())).unwrap();
    assert_eq!(cfg.listen, "0.0.0.0:9999");
    assert_eq!(cfg.budget, 12345);
    assert!(!cfg.mdns);
}

#[test]
fn env_overrides_the_toml_file() {
    let _guard = EnvGuard::new();
    let file = NamedTempFile::new().unwrap();
    std::fs::write(file.path(), r#"listen = "0.0.0.0:9999""#).unwrap();
    unsafe { env::set_var("MEDIAGRAM_CACHE_LISTEN", "127.0.0.1:1") };

    let cfg = load(Some(file.path())).unwrap();
    assert_eq!(cfg.listen, "127.0.0.1:1");
}

#[test]
fn env_overrides_apply_even_with_no_file_present() {
    let _guard = EnvGuard::new();
    unsafe { env::set_var("MEDIAGRAM_CACHE_BUDGET", "999") };

    let cfg = load(None).unwrap();
    assert_eq!(cfg.budget, 999);
}

#[test]
fn a_non_numeric_budget_env_override_fails_to_parse() {
    let _guard = EnvGuard::new();
    unsafe { env::set_var("MEDIAGRAM_CACHE_BUDGET", "not-a-number") };

    assert!(load(None).is_err());
}

#[test]
fn a_root_env_override_is_used() {
    let _guard = EnvGuard::new();
    unsafe { env::set_var("MEDIAGRAM_CACHE_ROOT", "/tmp/some-cache-root") };

    let cfg = load(None).unwrap();
    assert_eq!(cfg.root, std::path::PathBuf::from("/tmp/some-cache-root"));
}

#[test]
fn a_missing_file_at_an_explicit_path_is_an_error() {
    let _guard = EnvGuard::new();
    let err = load(Some(std::path::Path::new("/no/such/config.toml"))).unwrap_err();
    assert!(err.to_string().contains("config"));
}

#[test]
fn an_empty_mdns_env_value_does_not_override_the_default() {
    let _guard = EnvGuard::new();
    unsafe { env::set_var("MEDIAGRAM_CACHE_MDNS", "") };
    let cfg = load(None).unwrap();
    assert!(cfg.mdns);
}
