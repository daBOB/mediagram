use super::*;

use crate::test_env::EnvGuard;

#[test]
fn parses_minimal_toml_with_defaults() {
    let cfg: Config =
        toml::from_str("api_id = 1\napi_hash = \"h\"\nchannel = \"Library\"\n").unwrap();
    assert_eq!(cfg.part_size, DEFAULT_PART_SIZE);
    assert_eq!(cfg.max_attempts, 5);
    assert!(cfg.tmdb_key.is_none());
}

#[test]
fn empty_tmdb_key_in_file_loads_as_absent() {
    let _env_guard = EnvGuard::new();
    // A commented-out or blanked key must reach `add` as None so the
    // "set a key or pass --manual" guard fires instead of a TMDB 401.
    let mut f = tempfile::NamedTempFile::new().unwrap();
    std::io::Write::write_all(
        f.as_file_mut(),
        b"api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\ntmdb_key = \"\"\n",
    )
    .unwrap();
    let cfg = load(Some(f.path())).unwrap();
    assert!(cfg.tmdb_key.is_none());
}

#[test]
fn non_empty_tmdb_key_in_file_is_kept() {
    let _env_guard = EnvGuard::new();
    let mut f = tempfile::NamedTempFile::new().unwrap();
    std::io::Write::write_all(
        f.as_file_mut(),
        b"api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\ntmdb_key = \"k3y\"\n",
    )
    .unwrap();
    let cfg = load(Some(f.path())).unwrap();
    assert_eq!(cfg.tmdb_key.as_deref(), Some("k3y"));
}

#[test]
fn package_key_is_redacted_and_empty_loads_as_absent() {
    let _env_guard = EnvGuard::new();
    let mut f = tempfile::NamedTempFile::new().unwrap();
    std::io::Write::write_all(
        f.as_file_mut(),
        b"api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\npackage_key = \"\"\n",
    )
    .unwrap();
    assert!(load(Some(f.path())).unwrap().package_key.is_none());

    let cfg: Config = toml::from_str(
        "api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\npackage_key = \"c2VjcmV0\"\n",
    )
    .unwrap();
    let rendered = format!("{cfg:?}");
    assert!(!rendered.contains("c2VjcmV0"));
    assert!(rendered.contains("<redacted>"));
}

#[test]
fn debug_output_redacts_secrets() {
    let cfg: Config = toml::from_str(
        "api_id = 1\napi_hash = \"sekrit\"\nchannel = \"c\"\ntmdb_key = \"k3y\"\n",
    )
    .unwrap();
    let dbg = format!("{cfg:?}");
    assert!(!dbg.contains("sekrit") && !dbg.contains("k3y"));
    assert!(dbg.contains("<redacted>"));
}
