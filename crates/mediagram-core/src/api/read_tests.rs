use grammers_session::types::PeerAuth;
use rusqlite::Connection;

use super::*;

fn handles_naming(chat: i64, auth: i64) -> library::Handles {
    let mut handles = library::Handles::new();
    library::handle_for(
        &mut handles,
        library::LibraryEntry {
            chat,
            auth,
            title: "Films".into(),
        },
    );
    handles
}

/// The whole of what makes a set playable. Telegram answers a channel
/// addressed with its `access_hash` and refuses the same channel
/// addressed without one, so a part resolved against ambient authority
/// fails for every set in the library while the catalog still lists
/// them all.
#[test]
fn a_part_is_addressed_with_the_hash_recorded_for_its_channel() {
    let handles = handles_naming(-1_001_234_567_890, 7_654_321);

    let peer = channel_ref(&handles, -1_001_234_567_890).expect("a recorded channel");

    assert_eq!(peer.auth.hash(), 7_654_321);
    assert_ne!(peer.auth, PeerAuth::default(), "a channel refuses ambient authority");
}

/// Said plainly, and with the thing to do about it. Asking anyway would
/// spend a round trip to be told `CHANNEL_INVALID`, which reaches a
/// viewer as "could not be resolved" and names nothing they can act on.
#[test]
fn a_channel_this_device_never_recorded_says_so_rather_than_asking_anyway() {
    let handles = handles_naming(-1_001_234_567_890, 7_654_321);

    let refused = channel_ref(&handles, -1_001_999_999_999).unwrap_err();

    assert!(matches!(refused, CoreError::NotFound(_)));
    assert!(refused.to_string().contains("Choose the library again"));
}

#[test]
fn a_chat_id_that_is_not_a_dialog_id_is_a_broken_catalog_not_a_missing_channel() {
    let refused = channel_ref(&library::Handles::new(), 0).unwrap_err();
    assert!(matches!(refused, CoreError::Io(_)));
}

/// A minimal catalog with one set of one done part, just enough for
/// `read` to plan against. `status` is the set's; a playable one is
/// `complete`.
fn core_with_one_part(
    dir: &std::path::Path,
    set_id: &str,
    part_len: u64,
    status: &str,
) -> std::sync::Arc<Core> {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', ?2, 1, ?3, 0, 1)",
        rusqlite::params![set_id, part_len as i64, status],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
         VALUES (?1, 0, 0, ?2, -1001, 100, 'done')",
        rusqlite::params![set_id, part_len as i64],
    )
    .unwrap();
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// `len == 0` at an in-range, nonzero `offset` is the exact shape that
/// underflowed `end - offset` before the early return above existed:
/// `end` computed from a zero-length range landed one below `offset`.
/// Run under both `cargo test` (a debug-mode underflow panics) and
/// `cargo test --release` (it would otherwise wrap to a huge capacity).
#[tokio::test]
async fn a_zero_length_read_at_a_nonzero_offset_returns_empty() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_with_one_part(dir.path(), "01SET0000000000000000001", 1000, "complete");

    let bytes = read(&core, "01SET0000000000000000001".into(), 500, 0)
        .await
        .unwrap();

    assert!(bytes.is_empty());
}

/// Listing, sizing and streaming all refuse a set that is not playable;
/// reading its bytes must too, before any of them is fetched.
#[tokio::test]
async fn a_set_that_is_not_playable_cannot_be_read() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_with_one_part(dir.path(), "01SET0000000000000000002", 1000, "pending");

    let refused = read(&core, "01SET0000000000000000002".into(), 0, 10)
        .await
        .unwrap_err();

    assert!(matches!(refused, CoreError::NotFound(_)));
}
