use std::time::Duration;

use mlib_spec::subtitle_bundle::{BUNDLE_VERSION, BundleTrack};
use sha2::{Digest, Sha256};

use super::*;

/// One German track, encoded the way the uploader publishes a bundle.
fn bundle_bytes() -> Vec<u8> {
    subtitle_bundle::encode(&Bundle {
        v: BUNDLE_VERSION,
        set: "s1".into(),
        tracks: vec![BundleTrack {
            lang: "de".into(),
            forced: false,
            sdh: false,
            label: "Deutsch".into(),
            source: "embedded".into(),
            codec: "webvtt".into(),
            vtt: "WEBVTT\n\nHallo".into(),
        }],
    })
}

/// `name` in the cache directory, last used `age` ago.
fn held(core: &Core, name: &str, bytes: &[u8], age: Duration) -> PathBuf {
    std::fs::create_dir_all(dir(core)).unwrap();
    let path = dir(core).join(name);
    std::fs::write(&path, bytes).unwrap();
    let file = std::fs::File::open(&path).unwrap();
    file.set_modified(SystemTime::now() - age).unwrap();
    path
}

fn names_held(core: &Core) -> Vec<String> {
    let mut names: Vec<String> = std::fs::read_dir(dir(core))
        .unwrap()
        .map(|entry| entry.unwrap().file_name().into_string().unwrap())
        .collect();
    names.sort();
    names
}

#[tokio::test]
async fn a_bad_sha_shape_or_an_oversize_bundle_is_refused_before_any_fetch() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    let bad_shape = BundleRef {
        chat_id: -1,
        message_id: 1,
        bytes: 10,
        sha256: "not-hex".into(),
    };
    let oversize = BundleRef {
        chat_id: -1,
        message_id: 1,
        bytes: subtitle_bundle::MAX_COMPRESSED_BYTES as u64 + 1,
        sha256: "a".repeat(64),
    };

    assert!(fetch(&core, "s1", &bad_shape).await.is_none());
    assert!(fetch(&core, "s1", &oversize).await.is_none());
}

#[tokio::test]
async fn a_cached_bundle_is_read_without_any_network_route() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    let bytes = bundle_bytes();
    let sha = hex::encode(Sha256::digest(&bytes));
    std::fs::create_dir_all(dir(&core)).unwrap();
    std::fs::write(cached_path(&core, &sha), &bytes).unwrap();
    // A route that would fail if it were ever dialled — proving the hit
    // never reaches it.
    let bundle = BundleRef {
        chat_id: -999,
        message_id: -999,
        bytes: bytes.len() as u64,
        sha256: sha,
    };

    let decoded = fetch(&core, "s1", &bundle).await.unwrap();

    assert_eq!(decoded.tracks[0].vtt, "WEBVTT\n\nHallo");
}

/// A hit that fails to decode is treated as a miss and a refetch is
/// attempted — here failing cleanly for want of a route, rather than
/// serving the corrupt bytes.
#[tokio::test]
async fn a_corrupt_cached_file_is_never_served_and_a_refetch_is_attempted() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    let sha = "b".repeat(64);
    std::fs::create_dir_all(dir(&core)).unwrap();
    std::fs::write(cached_path(&core, &sha), b"not a bundle").unwrap();
    let bundle = BundleRef {
        chat_id: -1,
        message_id: 1,
        bytes: 10,
        sha256: sha,
    };

    assert!(fetch(&core, "s1", &bundle).await.is_none());
}

#[tokio::test]
async fn trim_to_budget_evicts_the_least_recently_used_files_first() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    for (name, age_secs) in [("old.json.gz", 20), ("mid.json.gz", 10), ("new.json.gz", 0)] {
        held(&core, name, &[0; 10], Duration::from_secs(age_secs));
    }

    trim_to_budget(&core, 15);

    assert_eq!(names_held(&core), ["new.json.gz"]);
}

/// Reading a bundle is what makes it recently used: the oldest file on disk
/// survives a trim once it has been read, and the one nobody read goes.
#[test]
fn a_hit_counts_as_use_for_the_next_trim() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    let bytes = bundle_bytes();
    let read = held(&core, "read.json.gz", &bytes, Duration::from_secs(20));
    held(
        &core,
        "unread.json.gz",
        &vec![0; bytes.len()],
        Duration::from_secs(10),
    );

    assert!(read_cached(&read).is_some());
    trim_to_budget(&core, bytes.len() as u64);

    assert_eq!(names_held(&core), ["read.json.gz"]);
}

/// A temporary file is a download still in flight, or one a crash left; it
/// is neither counted against the budget nor evicted from under its writer.
#[test]
fn a_trim_ignores_downloads_in_flight_and_keeps_what_fits() {
    let data = tempfile::tempdir().unwrap();
    let core = Core::at(data.path());
    trim_to_budget(&core, 0);
    assert!(!dir(&core).exists(), "no cache yet is nothing to trim");
    held(&core, "partial.1.tmp", &[0; 100], Duration::from_secs(60));
    held(&core, "kept.json.gz", &[0; 10], Duration::ZERO);

    trim_to_budget(&core, 50);

    assert_eq!(names_held(&core), ["kept.json.gz", "partial.1.tmp"]);
}

#[tokio::test]
async fn locks_serialize_the_same_sha_but_leave_others_free() {
    let locks = Locks::default();
    let a1 = locks.get("sha-a");
    let a2 = locks.get("sha-a");
    let b1 = locks.get("sha-b");

    let _held = a1.lock_owned().await;

    assert!(a2.try_lock_owned().is_err());
    assert!(b1.try_lock_owned().is_ok());
}
