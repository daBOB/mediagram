use std::os::unix::fs::symlink;

use super::*;

fn core_at(dir: &std::path::Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// Points `current` at `version`, the way `refresh.rs`'s `swap_current`
/// does, creating the version directory first.
fn point_current_at(core: &Core, version: &str) {
    let root = dir(core);
    std::fs::create_dir_all(root.join(version)).unwrap();
    let _ = std::fs::remove_file(root.join(CURRENT));
    symlink(version, root.join(CURRENT)).unwrap();
}

fn write_poster(base: &std::path::Path, key: &str) {
    std::fs::create_dir_all(base).unwrap();
    std::fs::write(base.join(format!("{key}.jpg")), b"fake-poster-bytes").unwrap();
}

/// Artwork outlives every version of the catalogue it was fetched for. A
/// refresh replaces the version directory wholesale, and a poster stored
/// inside one would be thrown away every time the app asked the channel for
/// the index. Outliving the library itself is a different question, and the
/// test below answers it the other way.
#[test]
fn a_fetched_poster_is_found_after_the_catalogue_is_replaced() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "v-1");

    let key = "tmdb-movie-550";
    write_poster(&artwork_dir(&core), key);

    // A refresh replaces the version directory wholesale, the way
    // `install_staged` does: the old one is removed, a new one takes its
    // place, and `current` is repointed.
    std::fs::remove_dir_all(dir(&core).join("v-1")).unwrap();
    point_current_at(&core, "v-2");

    assert!(poster_path(&core, key.into()).is_some());
}

/// Forgetting the library forgets its artwork with it.
///
/// The other half of where this directory lives, and the half that went
/// unwritten the first time: signing out deletes `catalog/` whole, so
/// artwork held anywhere else would outlive it and show the next account to
/// set this device up cached provider payloads naming the previous one's
/// titles. Stated as a containment rather than by deleting, because the
/// delete itself is the caller's — this crate only decides what falls inside
/// it.
#[test]
fn artwork_is_deleted_along_with_the_library_it_was_fetched_for() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());

    assert!(
        artwork_dir(&core).starts_with(dir(&core)),
        "artwork at {} would survive being signed out",
        artwork_dir(&core).display(),
    );
}

/// A published package carries its publisher's own chosen art, so it
/// stays authoritative for the keys it covers — a fetch only ever ran
/// for a title the package had nothing for.
#[test]
fn a_packages_own_poster_wins_over_a_fetched_one() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "v-1");

    let key = "tmdb-movie-550";
    write_poster(&current_dir(&core).join("posters"), key);
    write_poster(&artwork_dir(&core), key);

    let expected = current_dir(&core).join("posters").join(format!("{key}.jpg"));
    assert_eq!(poster_path(&core, key.into()), Some(expected.display().to_string()));
}

/// One title held in both places is one poster, not two. The System
/// screen reports this count beside the set count, and a number that
/// double-counts reads as artwork that is not there.
#[test]
fn a_key_held_in_both_places_is_counted_once() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "v-1");

    let key = "tmdb-movie-550";
    write_poster(&current_dir(&core).join("posters"), key);
    write_poster(&artwork_dir(&core), key);

    assert_eq!(count_posters(&current_dir(&core), &artwork_dir(&core)), 1);
}

/// The key is still validated before it reaches the filesystem. A second
/// lookup location must not become a second way past that check.
#[test]
fn an_invalid_key_resolves_to_nothing_in_either_location() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "v-1");

    // A file happens to sit at the path each location would build for this
    // key, so a pass here proves the validation guard runs before either
    // path is even built, not that the file is merely missing.
    let key = "not-a-valid-poster-key-42x";
    write_poster(&current_dir(&core).join("posters"), key);
    write_poster(&artwork_dir(&core), key);

    assert_eq!(poster_path(&core, key.into()), None);
}

/// The whole Refresh row on the System screen rests on this one value, and
/// it is read through `read_link` rather than from the name of a directory.
/// Every integration fixture builds `current` as a plain directory, where
/// `read_link` fails and the date comes back unknown — so `facts` has to be
/// asked here, against a `current` pointed the way a refresh points it.
#[test]
fn the_installed_catalogues_push_time_is_read_off_the_symlink() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "v-1758300000");

    assert_eq!(facts(&core).published_at, Some(1_758_300_000));
}

/// A catalogue installed by a version of this app that did not name its
/// directories for the push time reads as unknown rather than as a date it
/// made up. The screen leaves the row out; it does not print a wrong age.
#[test]
fn a_catalogue_with_no_push_time_in_its_name_reports_none() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "not-one-of-ours");

    assert_eq!(facts(&core).published_at, None);
}

/// The version directory is named for when the index was pushed, so the
/// catalogue's age needs no separate record. A name that is not one of
/// ours reads as unknown rather than as a wrong date.
#[test]
fn a_version_directory_name_carries_its_push_time() {
    assert_eq!(pushed_at_of("v-1758300000"), Some(1_758_300_000));
    assert_eq!(pushed_at_of("v-0"), Some(0));
    assert_eq!(pushed_at_of("current"), None);
    assert_eq!(pushed_at_of("v-"), None);
    assert_eq!(pushed_at_of("v-not-a-number"), None);
    assert_eq!(pushed_at_of(""), None);
}
