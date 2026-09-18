//! The uploader warns about what the player would convert.
//!
//! "Direct play" is defined twice, once in `media::direct_play` and once in
//! the player's `playable.js`, because the two run in different languages at
//! different moments: the uploader answers before the bytes move, the player
//! answers per request. A copy that drifts is worse than no copy — the
//! uploader would promise a conversion that never happens, or stay quiet
//! about one that does, and nothing would fail until someone pressed play.
//!
//! This test fails when the two stop matching. Edit the Rust lists; this
//! points at what else to update.

use mediagram::media::direct_play;

const PLAYER_POLICY: &str = "../../web/public/lib/playable.js";

/// The members of `export const <name> = new Set([...])`, in source order.
fn player_set(source: &str, name: &str) -> Vec<String> {
    let needle = format!("export const {name} = new Set([");
    let start = source
        .find(&needle)
        .unwrap_or_else(|| panic!("no `{name}` set in {PLAYER_POLICY}"))
        + needle.len();
    let end = start
        + source[start..]
            .find("])")
            .unwrap_or_else(|| panic!("`{name}` set is not closed in {PLAYER_POLICY}"));
    source[start..end]
        .split(',')
        .map(|item| item.trim().trim_matches('"').trim_matches('\'').to_string())
        .filter(|item| !item.is_empty())
        .collect()
}

fn read_player() -> String {
    let path = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join(PLAYER_POLICY);
    std::fs::read_to_string(&path).unwrap_or_else(|e| panic!("reading {}: {e}", path.display()))
}

fn assert_matches(rust: &[&str], name: &str) {
    let source = read_player();
    let mut player = player_set(&source, name);
    let mut ours: Vec<String> = rust.iter().map(|s| s.to_string()).collect();
    player.sort();
    ours.sort();
    assert_eq!(
        ours, player,
        "`{name}` differs between media::direct_play and {PLAYER_POLICY}"
    );
}

#[test]
fn containers_match_the_player() {
    assert_matches(&direct_play::CONTAINERS, "CONTAINERS");
}

#[test]
fn video_codecs_match_the_player() {
    assert_matches(&direct_play::VIDEO, "VIDEO");
}

#[test]
fn audio_codecs_match_the_player() {
    assert_matches(&direct_play::AUDIO, "AUDIO");
}

/// The lists decide whether a real file is reported as needing conversion,
/// so a spelling that never matches anything would be silently useless.
#[test]
fn codec_names_are_compared_the_way_ffprobe_spells_them() {
    assert!(direct_play::known(&direct_play::VIDEO, "H264"));
    assert!(direct_play::known(&direct_play::AUDIO, "AAC"));
    assert!(!direct_play::known(&direct_play::VIDEO, "hevc"));
    assert!(!direct_play::known(&direct_play::AUDIO, "eac3"));
    assert!(!direct_play::known(&direct_play::CONTAINERS, "mkv"));
}

// What the lists mean when applied to a real file. The uploader warns from
// this and `prepare --mp4` decides from it, so the two cannot answer
// differently the way two hand-written copies did.

use mediagram::media::direct_play::{Blocker, blockers, plays_directly};
use mediagram::media::prepare_plan::{Stream, StreamKind};
use std::path::Path;

fn stream(kind: StreamKind, codec: &str) -> Stream {
    Stream {
        index: 0,
        kind,
        language: None,
        bit_rate: None,
        codec: Some(codec.into()),
    }
}

#[test]
fn an_mp4_of_h264_and_aac_plays_as_it_is() {
    let streams = [
        stream(StreamKind::Video, "h264"),
        stream(StreamKind::Audio, "aac"),
    ];

    assert!(blockers(Path::new("a.mp4"), &streams).is_empty());
    assert!(plays_directly(Path::new("a.mp4"), &streams));
}

#[test]
fn matroska_is_reported_in_the_words_the_player_uses() {
    let streams = [stream(StreamKind::Video, "h264")];

    assert_eq!(
        blockers(Path::new("a.mkv"), &streams),
        vec![Blocker::Container("mkv".into())]
    );
    assert_eq!(
        blockers(Path::new("a.mkv"), &streams)[0].reason(),
        "Matroska container"
    );
}

/// The bug this function exists to prevent: checking only the first audio
/// track calls a file fine and leaves every other track unplayable.
#[test]
fn every_audio_track_is_judged_not_only_the_first() {
    let streams = [
        stream(StreamKind::Video, "h264"),
        stream(StreamKind::Audio, "aac"),
        stream(StreamKind::Audio, "eac3"),
    ];

    assert_eq!(
        blockers(Path::new("a.mp4"), &streams),
        vec![Blocker::Audio("eac3".into())]
    );
}

#[test]
fn a_reason_is_given_once_however_many_tracks_share_it() {
    let streams = [
        stream(StreamKind::Audio, "ac3"),
        stream(StreamKind::Audio, "ac3"),
        stream(StreamKind::Audio, "ac3"),
    ];

    assert_eq!(
        blockers(Path::new("a.mp4"), &streams),
        vec![Blocker::Audio("ac3".into())]
    );
}

/// The distinction the warning turns on: a wrapper and an audio track are
/// rewritten in seconds, a picture has to be re-encoded.
#[test]
fn only_the_picture_is_beyond_what_prepare_can_fix() {
    assert!(Blocker::Container("mkv".into()).fixable_by_prepare());
    assert!(Blocker::Audio("eac3".into()).fixable_by_prepare());
    assert!(!Blocker::Video("hevc".into()).fixable_by_prepare());
}

/// An HEVC file in a Matroska wrapper has both kinds of problem, and saying
/// only one of them would send someone to run a command that cannot help.
#[test]
fn a_file_can_be_blocked_by_both_kinds_at_once() {
    let streams = [
        stream(StreamKind::Video, "hevc"),
        stream(StreamKind::Audio, "ac3"),
    ];

    let found = blockers(Path::new("a.mkv"), &streams);

    assert_eq!(found.len(), 3);
    assert_eq!(found.iter().filter(|b| b.fixable_by_prepare()).count(), 2);
    assert_eq!(found.iter().filter(|b| !b.fixable_by_prepare()).count(), 1);
}

#[test]
fn a_file_with_no_extension_is_not_a_container_anyone_knows() {
    assert_eq!(
        blockers(Path::new("nameless"), &[])[0].reason(),
        "unknown container"
    );
}
