use mlib_spec::Kind;

use crate::versions::install_staged;

use super::*;

/// A row as a fetch would record it. `TitleDetailsRow` carries no source of its own
/// — `upsert` writes the one the index writes — and names the kind with the
/// provider's own enum rather than with text.
fn described(kind: Kind, id: u64, overview: &str) -> TitleDetailsRow {
    TitleDetailsRow {
        kind,
        id,
        lang: "en-US".into(),
        overview: Some(overview.into()),
        tagline: None,
        genres: None,
        rating: None,
        network: None,
        status: None,
        first_air: None,
        last_air: None,
        total_seasons: None,
        total_episodes: None,
        certification: None,
        popularity: None,
        collection_id: None,
        collection_name: None,
        series_type: None,
    }
}

/// The sidecar sits beside the version directories, not inside one. A
/// refresh replaces a version wholesale, and anything kept within it is
/// deleted every time the app asks the channel for the index.
#[test]
fn the_sidecar_survives_the_refreshes_that_follow_it() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    std::fs::create_dir_all(store::dir(&core).join("v-1")).unwrap();

    let conn = open_or_create(&core).unwrap();
    upsert(
        &conn,
        &described(Kind::Movie, 550, "Ein Kellner in Seifenblasen"),
    )
    .unwrap();
    drop(conn);

    // A real refresh rather than a stand-in for one. `install_staged` is
    // both destructive passes at once: it renames the staged directory over
    // the version, repoints `current`, and then sweeps every other version
    // and any leftover staging directory. Removing the old `v-…` directory
    // by hand would exercise neither, and would miss a sidecar kept
    // somewhere the rename or the sweep reaches — `incoming` above all,
    // which is a real place to put a file and one no refresh leaves
    // standing.
    let incoming = store::dir(&core).join("incoming");
    std::fs::create_dir_all(&incoming).unwrap();
    install_staged(&store::dir(&core), &incoming, "v-2").unwrap();

    let conn = open_or_create(&core).unwrap();
    assert!(read(&conn, "tmdb-movie-550").unwrap().is_some());
}

/// Forgetting the library forgets what was learned about it. The rows name
/// the previous account's titles, and a sign-out that left them behind would
/// be a sign-out in name only. Stated as a containment rather than by
/// deleting, because the delete itself is the caller's.
#[test]
fn the_sidecar_is_deleted_along_with_the_library_it_describes() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());

    assert!(
        details_db(&core).starts_with(store::dir(&core)),
        "descriptions at {} would survive being signed out",
        details_db(&core).display(),
    );
}

/// A key that begins with `tmdb-` and still carries an injection payload in
/// its `kind` is refused against the sidecar too, not only against the index
/// where the same test already stands.
///
/// The row is planted under the exact malicious `kind` — with raw SQL, since
/// `upsert` takes a `Kind` and could not write one — so a pass proves the key
/// never reached this query rather than merely matching nothing in it.
#[test]
fn an_injection_payload_is_refused_against_the_sidecar_too() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());

    let kind = "movie'; DROP TABLE shows;";
    let conn = open_or_create(&core).unwrap();
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview)
         VALUES ('tmdb', ?1, 550, 'en-US', 'should never be read')",
        rusqlite::params![kind],
    )
    .unwrap();
    drop(conn);

    assert!(title_info(&core, format!("tmdb-{kind}-550")).is_none());
}
