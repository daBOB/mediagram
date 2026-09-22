//! What a browser can open without the player converting it first.
//!
//! The player decides this for itself, in `web/public/lib/playable.js`, and
//! has to: it answers the question per request, from the catalog, in another
//! runtime. The uploader needs the same answer earlier — before bytes move —
//! so it can say that a file will be converted on every play and what would
//! fix it.
//!
//! Two copies of a rule is one more than is safe, so
//! `tests/shared_direct_play_policy.rs` fails when they stop matching. Edit
//! these lists; that test points at what else to update.

use std::path::Path;

use crate::media::streams::{Stream, StreamKind};

/// Containers a browser will open. Matroska is not one of them.
pub const CONTAINERS: [&str; 3] = ["mp4", "m4v", "webm"];

/// Video codecs that play essentially everywhere.
pub const VIDEO: [&str; 6] = ["h264", "avc", "avc1", "vp8", "vp9", "av1"];

/// Audio codecs that play essentially everywhere.
pub const AUDIO: [&str; 5] = ["aac", "mp4a", "opus", "vorbis", "mp3"];

/// Whether `codec` is in `set`, comparing the way ffprobe spells things.
pub fn known(set: &[&str], codec: &str) -> bool {
    set.iter().any(|k| k.eq_ignore_ascii_case(codec))
}

/// One reason a file will be converted on every play.
///
/// The split is the whole point: a wrapper and an audio track are rewritten
/// in seconds with the picture untouched, and a picture a browser will not
/// open has to be re-encoded. Telling someone to run `prepare` on the second
/// kind costs them hours and changes nothing.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Blocker {
    /// The wrapper. `prepare --mp4` fixes it.
    Container(String),
    /// An audio track. `prepare --mp4` fixes it.
    Audio(String),
    /// The picture itself, which no rewrite of the wrapper can change.
    Video(String),
}

impl Blocker {
    /// Whether rewriting the wrapper and the audio would clear this.
    pub fn fixable_by_prepare(&self) -> bool {
        !matches!(self, Blocker::Video(_))
    }

    /// The reason in the words the player uses for the same file.
    pub fn reason(&self) -> String {
        match self {
            Blocker::Container(name) if name == "mkv" => "Matroska container".to_string(),
            Blocker::Container(name) if name.is_empty() => "unknown container".to_string(),
            Blocker::Container(name) => format!("{name} container"),
            Blocker::Audio(codec) => format!("{codec} audio"),
            Blocker::Video(codec) => format!("{codec} video"),
        }
    }
}

/// Every reason this file will not play as it stands, in the order a viewer
/// would notice them.
///
/// Every audio track is asked about, not just the first: a file the player
/// hands over whole is one where nothing a viewer might switch to needs
/// converting. Checking only the first track would call a file fine and leave
/// the German track unplayable.
pub fn blockers(path: &Path, streams: &[Stream]) -> Vec<Blocker> {
    let mut found = Vec::new();

    let container = path
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase();
    if !known(&CONTAINERS, &container) {
        found.push(Blocker::Container(container));
    }

    if let Some(codec) = codec_of(streams, StreamKind::Video)
        && !known(&VIDEO, &codec)
    {
        found.push(Blocker::Video(codec));
    }

    let mut unplayable: Vec<String> = streams
        .iter()
        .filter(|s| s.kind == StreamKind::Audio)
        .filter_map(|s| s.codec.clone())
        .filter(|codec| !known(&AUDIO, codec))
        .collect();
    unplayable.dedup();
    found.extend(unplayable.into_iter().map(Blocker::Audio));

    found
}

/// Whether a browser could open this file as it stands.
pub fn plays_directly(path: &Path, streams: &[Stream]) -> bool {
    blockers(path, streams).is_empty()
}

/// The codec of the first stream of a kind, which is the one a player uses.
pub fn codec_of(streams: &[Stream], kind: StreamKind) -> Option<String> {
    streams
        .iter()
        .find(|s| s.kind == kind)
        .and_then(|s| s.codec.clone())
}
