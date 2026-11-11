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
    let cfg: Config =
        toml::from_str("api_id = 1\napi_hash = \"sekrit\"\nchannel = \"c\"\ntmdb_key = \"k3y\"\n")
            .unwrap();
    let dbg = format!("{cfg:?}");
    assert!(!dbg.contains("sekrit") && !dbg.contains("k3y"));
    assert!(dbg.contains("<redacted>"));
}

#[test]
fn the_cache_server_keys_are_read() {
    let cfg: Config = toml::from_str(
        "api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\ncache_server = \"http://127.0.0.1:7788\"\ncache_server_token = \"t0ken\"\n",
    )
    .unwrap();
    assert_eq!(cfg.cache_server.as_deref(), Some("http://127.0.0.1:7788"));
    assert_eq!(cfg.cache_server_token.as_deref(), Some("t0ken"));
    assert!(
        cfg.home_cache(&mediagram_core::http::client().unwrap())
            .is_some()
    );
    assert!(!format!("{cfg:?}").contains("t0ken"));
}

#[test]
fn no_cache_server_means_no_home_cache() {
    let cfg: Config =
        toml::from_str("api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\ncache_server = \"\"\n")
            .unwrap();
    assert!(
        cfg.home_cache(&mediagram_core::http::client().unwrap())
            .is_none()
    );
}

/// With no key there is nothing to ask TMDB with: a title neither cache
/// holds is the keyless error, not a request TMDB would refuse.
#[tokio::test]
async fn a_keyless_config_asks_tmdb_nothing() {
    use mediagram_tmdb::tmdb_client::TmdbApi;
    for key in [None, Some(String::new())] {
        let dir = tempfile::tempdir().unwrap();
        let mut cfg: Config =
            toml::from_str("api_id = 1\napi_hash = \"h\"\nchannel = \"c\"\n").unwrap();
        cfg.tmdb_key = key;
        cfg.data_dir = Some(dir.path().to_path_buf());
        let api = cfg
            .tmdb_client(mediagram_core::http::client().unwrap())
            .unwrap();
        let err = api.get_json("/movie/1", &[]).await.unwrap_err();
        assert_eq!(
            err.to_string(),
            "no TMDB key, and the home cache server does not hold this"
        );
    }
}
