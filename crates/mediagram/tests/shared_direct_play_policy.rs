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
