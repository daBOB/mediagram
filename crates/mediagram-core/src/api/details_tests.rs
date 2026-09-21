use std::os::unix::fs::symlink;

use mlib_spec::Kind;

use crate::shows::read;

use super::*;

fn core_at(dir: &std::path::Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// Points `current` at `version`, the way `refresh.rs`'s `swap_current`
/// does, creating the version directory first.
fn point_current_at(core: &Core, version: &str) {
    let root = catalog::dir(core);
    std::fs::create_dir_all(root.join(version)).unwrap();
    let _ = std::fs::remove_file(root.join(catalog::CURRENT));
    symlink(version, root.join(catalog::CURRENT)).unwrap();
}

/// A row as a fetch would record it. `ShowRow` carries no source of its own
/// — `upsert` writes the one the index writes — and names the kind with the
/// provider's own enum rather than with text.
fn described(kind: Kind, id: u64, overview: &str) -> ShowRow {
    ShowRow {
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
    }
}

/// The sidecar sits beside the version directories, not inside one. A
/// refresh replaces a version wholesale, and anything kept within it is
/// deleted every time the app asks the channel for the index.
#[test]
fn the_sidecar_survives_the_refreshes_that_follow_it() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "v-1");

    let conn = open_or_create(&core).unwrap();
    upsert(&conn, &described(Kind::Movie, 550, "Ein Kellner in Seifenblasen")).unwrap();
    drop(conn);

    // A refresh replaces the version directory wholesale, the way
    // `install_staged` does: the old one is removed, a new one takes its
    // place, and `current` is repointed.
    std::fs::remove_dir_all(catalog::dir(&core).join("v-1")).unwrap();
    point_current_at(&core, "v-2");

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
    let core = core_at(data.path());

    assert!(
        details_db(&core).starts_with(catalog::dir(&core)),
        "descriptions at {} would survive being signed out",
        details_db(&core).display(),
    );
}

/// The schema is written in one place. A column added to the shared
/// migrations reaches this database too, or the two silently disagree about
/// what a row holds.
#[test]
fn the_sidecar_is_built_from_the_shared_migrations() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    let conn = open_or_create(&core).unwrap();

    let columns: Vec<String> = conn
        .prepare("SELECT name FROM pragma_table_info('shows')")
        .unwrap()
        .query_map([], |row| row.get(0))
        .unwrap()
        .collect::<rusqlite::Result<_>>()
        .unwrap();

    for expected in [
        "source",
        "kind",
        "id",
        "lang",
        "overview",
        "tagline",
        "genres",
        "rating",
        "network",
        "status",
        "total_seasons",
        "total_episodes",
    ] {
        assert!(columns.iter().any(|c| c == expected), "no {expected} column");
    }
}

/// Asking twice replaces rather than duplicates: a later fetch is a
/// correction, not a second opinion.
#[test]
fn a_second_fetch_of_one_title_replaces_the_first() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    let conn = open_or_create(&core).unwrap();

    upsert(&conn, &described(Kind::Movie, 550, "first")).unwrap();
    upsert(&conn, &described(Kind::Movie, 550, "second")).unwrap();

    let row = read(&conn, "tmdb-movie-550").unwrap().unwrap();
    assert_eq!(row.overview.as_deref(), Some("second"));
}

/// Reopening a sidecar that is already at this build's schema applies no
/// statement a second time: SQLite has no `ADD COLUMN IF NOT EXISTS`, so a
/// replayed migration list would fail and take the store with it.
#[test]
fn reopening_an_existing_sidecar_replays_no_migration() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());

    drop(open_or_create(&core).unwrap());

    assert!(open_or_create(&core).is_ok());
}
