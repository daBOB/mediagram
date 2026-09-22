use super::*;
use rusqlite::Connection;

/// Installs a catalog under `version`, the way a refresh does: an empty but
/// readable index is staged as `incoming` and handed to the one function
/// that ever makes a version current.
fn install_version(core: &Core, version: &str) {
    let incoming = store::dir(core).join("incoming");
    std::fs::create_dir_all(&incoming).unwrap();
    let conn = Connection::open(incoming.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    drop(conn);
    crate::versions::install_staged(&store::dir(core), &incoming, version).unwrap();
}

/// Artwork a fetch wrote outlives the refreshes that follow it.
///
/// Driven through the real removal paths rather than asserted about as a
/// string, because this is the defect as it actually shipped: the artwork
/// went inside the version directory, and a refresh runs on every catalog
/// load, so it was deleted before it was ever shown — counted on a device at
/// 0, then 236, then 0 again across a restart. `poster_path` reads both the
/// version's own `posters/` and the artwork directory, so a poster planted
/// by hand is found either way and nothing else here would notice.
///
/// Both ways an install replaces a version run here: the same version
/// installed again beside itself, and a new one, each followed by
/// `remove_other_versions` clearing every version but the one just published.
#[test]
fn a_fetched_poster_outlives_the_refreshes_that_follow_it() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::new(data.path().display().to_string(), 1, "h".into());
    install_version(&core, "v-1");

    let plan = plan_fetch(&core, "en-US").expect("the installed catalog is readable");
    std::fs::create_dir_all(&plan.artwork_dir).unwrap();
    let poster = plan.artwork_dir.join("tmdb-movie-550.jpg");
    std::fs::write(&poster, b"fake-poster-bytes").unwrap();

    // The same version installed again, which replaces the copy in place.
    install_version(&core, "v-1");
    assert!(poster.exists(), "reinstalling a version took the artwork with it");

    // A new version: `remove_other_versions` clears the one just replaced.
    install_version(&core, "v-2");
    assert!(poster.exists(), "refreshing to a new version took the artwork with it");
}

/// The error `TmdbClient::get_json` returns for a non-success answer, with
/// the context a caching wrapper adds on top, so the check has to find it
/// in the chain rather than at the top.
fn tmdb_style_error(path: &str, status: reqwest::StatusCode, body: &str) -> anyhow::Error {
    anyhow::Error::from(HttpStatus {
        path: path.to_string(),
        status: status.as_u16(),
        body: body.to_string(),
    })
    .context(format!("asking for {path}"))
}

#[test]
fn a_401_response_is_recognised_as_a_rejected_key() {
    let err = tmdb_style_error(
        "/authentication",
        reqwest::StatusCode::UNAUTHORIZED,
        r#"{"status_code":7,"status_message":"Invalid API key.","success":false}"#,
    );
    assert!(rejected_the_key(&err));
}

/// A title the provider does not have is a different failure than a key it
/// will not accept, and must not be folded into the same one.
#[test]
fn a_404_response_is_not_mistaken_for_a_rejected_key() {
    let err = tmdb_style_error(
        "/movie/999999999",
        reqwest::StatusCode::NOT_FOUND,
        r#"{"status_code":34,"status_message":"The resource could not be found.","success":false}"#,
    );
    assert!(!rejected_the_key(&err));
}

/// A transport failure has no status at all, even one whose wording
/// happens to mention 401.
#[test]
fn a_transport_failure_is_not_mistaken_for_a_rejected_key() {
    let err = anyhow::anyhow!("tmdb request to /authentication failed with 401 Unauthorized");
    assert!(!rejected_the_key(&err));
}

/// A `reqwest::Client` built with no crypto provider installed panics at
/// construction. `TmdbClient` takes the client `http::client()` builds,
/// which installs one first, so building it must succeed in this crate
/// alone — `cargo test -p mediagram-core`, the scope Android compiles, has
/// no other crate installing a provider for it. No network is reached.
#[test]
fn the_real_tmdb_client_builds_from_this_crates_own_client() {
    let client = http::client().expect("this crate's own client builds");
    let _ = TmdbClient::new(client, "fake-key");
}
