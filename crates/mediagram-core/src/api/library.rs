//! Naming a channel without naming it.
//!
//! The surface above this crate picks a library from a list of titles and
//! sends back a *handle*. A handle is a random 128-bit name this data
//! directory minted for that channel and wrote down here — it is not the
//! channel's identifier, it is not computed from one, and two data
//! directories mint different handles for the same channel. That is what
//! keeps the rule this whole surface is held to: the caller is told what it
//! may play, never where the bytes live, so no `chat_id` may cross the
//! boundary in a value, a log, or an error.
//!
//! The map itself never leaves the crate. It lives beside the auth key, owner
//! only, and is deleted with it when a device is signed out.

use std::collections::BTreeMap;
use std::io::Write;
use std::os::unix::fs::OpenOptionsExt;
use std::path::{Path, PathBuf};

use grammers_session::types::{PeerAuth, PeerId, PeerRef};
use serde::{Deserialize, Serialize};

use super::{Core, CoreError};

const LIBRARIES_FILE: &str = "libraries.json";

/// What one handle stands for. Private to this crate by construction: it is
/// only ever read back out of the file below, and only ever to build a
/// [`PeerRef`] for a request.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub(super) struct LibraryEntry {
    /// The channel, in the same Bot API dialog-id form `parts.chat_id` uses.
    pub chat: i64,
    /// Telegram's `access_hash` for it, which the account needs to address it.
    pub auth: i64,
    pub title: String,
}

impl LibraryEntry {
    /// `None` when the recorded id is not a dialog id any more — a hand-edited
    /// or truncated file, never something a write from here produces.
    pub(super) fn peer(&self) -> Option<PeerRef> {
        PeerId::from_bot_api_dialog_id(self.chat).map(|id| PeerRef {
            id,
            auth: PeerAuth::from_hash(self.auth),
        })
    }
}

pub(super) type Handles = BTreeMap<String, LibraryEntry>;

pub(super) fn path(core: &Core) -> PathBuf {
    core.data_dir.join(LIBRARIES_FILE)
}

/// The handles this device has minted, or an empty map on a first run.
///
/// A file that exists but will not parse is an error rather than an empty
/// map: folding the two together would silently orphan the handle already
/// chosen and send a working device back to the picker with no explanation.
pub(super) fn read(path: &Path) -> Result<Handles, CoreError> {
    let text = match std::fs::read_to_string(path) {
        Ok(text) => text,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(Handles::new()),
        Err(err) => return Err(CoreError::io("reading the stored libraries")(err)),
    };
    serde_json::from_str(&text).map_err(CoreError::io(
        "the stored list of libraries is corrupt; starting over clears it",
    ))
}

/// Writes the map owner-only, through a temporary file renamed over the old
/// one, so a process killed mid-write leaves the previous map intact rather
/// than a half-written one that would not parse.
pub(super) fn write(path: &Path, handles: &Handles) -> Result<(), CoreError> {
    const RECORDING: &str = "recording the chosen library";
    let text = serde_json::to_string(handles).map_err(CoreError::io(RECORDING))?;
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(CoreError::io(RECORDING))?;
    }
    let staged = path.with_extension("json.new");
    let _ = std::fs::remove_file(&staged);
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .mode(0o600)
        .create_new(true)
        .open(&staged)
        .map_err(CoreError::io(RECORDING))?;
    file.write_all(text.as_bytes()).map_err(CoreError::io(RECORDING))?;
    drop(file);
    std::fs::rename(&staged, path).map_err(CoreError::io(RECORDING))
}

/// The handle this device already uses for `entry`'s channel, or a fresh one.
///
/// Stable across listings, because the handle a caller stored has to keep
/// resolving; refreshed in place, because a channel can be renamed and an
/// `access_hash` reissued without becoming a different channel.
pub(super) fn handle_for(handles: &mut Handles, entry: LibraryEntry) -> String {
    let existing = handles
        .iter()
        .find(|(_, held)| held.chat == entry.chat)
        .map(|(handle, _)| handle.clone());
    let handle = existing.unwrap_or_else(mint);
    handles.insert(handle.clone(), entry);
    handle
}

/// The way to address `chat`, from what this device recorded when it last
/// listed the account's libraries.
///
/// Telegram refuses a channel addressed without an `access_hash` —
/// `CHANNEL_INVALID`, whoever is asking — and the ambient authority a bare
/// id carries is only ever enough for a bot or a contact. A channel is
/// neither. This crate persists the auth key and nothing else, so grammers'
/// own peer cache is empty on every launch and cannot answer; the map
/// written beside that key can, and is refreshed every time the list is.
pub(super) fn peer_for_chat(handles: &Handles, chat: i64) -> Option<PeerRef> {
    handles
        .values()
        .find(|entry| entry.chat == chat)
        .and_then(LibraryEntry::peer)
}

pub(super) fn lookup(core: &Core, handle: &str) -> Result<LibraryEntry, CoreError> {
    read(&path(core))?.remove(handle).ok_or_else(|| {
        CoreError::NotFound("this device no longer has that library stored".into())
    })
}

/// 128 bits from the OS, which is what makes a handle unguessable and, more
/// to the point, unrelated to the channel it names: nothing about the
/// channel goes into it, so no arithmetic takes it back.
fn mint() -> String {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    hex::encode(bytes)
}

#[cfg(test)]
mod tests {
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
}
