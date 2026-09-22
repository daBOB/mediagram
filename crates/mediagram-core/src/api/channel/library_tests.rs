use super::*;

fn entry(chat: i64) -> LibraryEntry {
    LibraryEntry {
        chat,
        auth: 7_654_321,
        title: "Films".into(),
    }
}

/// The rule the whole surface rests on. If a handle were any function of
/// the channel it names, minting one twice would give the same answer
/// both times — so two different answers prove no such function exists,
/// and prove it without anyone having to read the code that mints them.
#[test]
fn a_handle_is_not_derivable_from_the_channel_it_names() {
    let mut one = Handles::new();
    let mut other = Handles::new();

    let first = handle_for(&mut one, entry(-1_001_234_567_890));
    let second = handle_for(&mut other, entry(-1_001_234_567_890));

    assert_ne!(first, second, "a handle computed from a chat_id is a chat_id");
}

/// The weaker half of the same rule, checked separately because it is the
/// one a careless "just encode the id" change would break: the text of a
/// handle must carry no rendering of the channel's id.
#[test]
fn a_handle_carries_no_rendering_of_the_channel_id() {
    let chat: i64 = -1_001_234_567_890;
    let mut handles = Handles::new();

    let handle = handle_for(&mut handles, entry(chat));

    for rendering in [
        chat.to_string(),
        chat.abs().to_string(),
        format!("{:x}", chat.abs()),
        hex::encode(chat.to_be_bytes()),
        hex::encode(chat.to_le_bytes()),
    ] {
        assert!(!handle.contains(&rendering), "handle leaks {rendering}");
    }
}

/// A stored handle has to keep resolving, so a second listing must not
/// rename what the caller already chose.
#[test]
fn listing_a_channel_again_keeps_the_handle_already_given_out() {
    let mut handles = Handles::new();
    let first = handle_for(&mut handles, entry(-1_001_234_567_890));

    let again = handle_for(&mut handles, entry(-1_001_234_567_890));

    assert_eq!(first, again);
    assert_eq!(handles.len(), 1);
}

#[test]
fn a_renamed_channel_keeps_its_handle_and_gains_the_new_title() {
    let mut handles = Handles::new();
    let handle = handle_for(&mut handles, entry(-1_001_234_567_890));

    let renamed = LibraryEntry {
        title: "Family films".into(),
        ..entry(-1_001_234_567_890)
    };
    assert_eq!(handle, handle_for(&mut handles, renamed));
    assert_eq!(handles[&handle].title, "Family films");
}

#[test]
fn two_channels_get_two_handles() {
    let mut handles = Handles::new();
    let first = handle_for(&mut handles, entry(-1_001_234_567_890));
    let second = handle_for(&mut handles, entry(-1_001_999_999_999));
    assert_ne!(first, second);
    assert_eq!(handles.len(), 2);
}

#[test]
fn a_written_map_reads_back_equal() {
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join(LIBRARIES_FILE);
    let mut handles = Handles::new();
    handle_for(&mut handles, entry(-1_001_234_567_890));

    write(&file, &handles).unwrap();

    assert_eq!(read(&file).unwrap(), handles);
}

#[test]
fn no_file_reads_back_as_nothing_chosen_yet() {
    let dir = tempfile::tempdir().unwrap();
    assert!(read(&dir.path().join(LIBRARIES_FILE)).unwrap().is_empty());
}

/// The same reasoning the package identity record is held to: an absent
/// file and a corrupt one mean different things and must not be folded
/// together.
#[test]
fn a_corrupt_map_is_refused_rather_than_read_as_empty() {
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join(LIBRARIES_FILE);
    std::fs::write(&file, b"not json").unwrap();
    assert!(read(&file).is_err());
}

/// This file names every channel the account can see. It sits beside the
/// auth key and is protected the same way.
#[test]
fn the_stored_map_is_never_group_or_world_readable() {
    use std::os::unix::fs::PermissionsExt;
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join(LIBRARIES_FILE);

    write(&file, &Handles::new()).unwrap();
    write(&file, &Handles::new()).unwrap();

    let mode = std::fs::metadata(&file).unwrap().permissions().mode();
    assert_eq!(mode & 0o777, 0o600);
}

/// The byte path's whole dependency on this file. Telegram answers a
/// channel addressed with its `access_hash` and refuses the same channel
/// addressed without one, so a lookup that lost the hash would leave
/// every set in the library unplayable while the catalog still listed
/// them all.
#[test]
fn a_recorded_channel_is_addressable_with_the_hash_it_was_recorded_with() {
    let mut handles = Handles::new();
    handle_for(&mut handles, entry(-1_001_234_567_890));

    let peer = peer_for_chat(&handles, -1_001_234_567_890).expect("a recorded channel");

    assert_eq!(peer.id.bot_api_dialog_id(), Some(-1_001_234_567_890));
    assert_eq!(peer.auth.hash(), 7_654_321);
    assert_ne!(
        peer.auth,
        PeerAuth::default(),
        "ambient authority is refused for a channel, whoever is asking",
    );
}

#[test]
fn a_channel_this_device_never_listed_cannot_be_addressed_from_here() {
    let mut handles = Handles::new();
    handle_for(&mut handles, entry(-1_001_234_567_890));

    assert!(peer_for_chat(&handles, -1_001_999_999_999).is_none());
}

#[test]
fn a_recorded_entry_addresses_the_channel_it_came_from() {
    let peer = entry(-1_001_234_567_890).peer().expect("a real dialog id");
    assert_eq!(peer.id.bot_api_dialog_id(), Some(-1_001_234_567_890));
    assert_eq!(peer.auth.hash(), 7_654_321);
}

#[test]
fn an_unusable_recorded_id_addresses_nothing() {
    assert!(entry(0).peer().is_none());
}
