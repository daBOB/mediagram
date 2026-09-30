//! The subtitle bundle: a gzip'd JSON document, one per set, carrying every
//! subtitle track the uploader extracted from it. The durable copy — a
//! `library.db` row (`subtitle_tracks`) only summarises a track so a reader
//! can show a label and pick one before ever fetching this file.
//!
//! Also the channel message it travels as (spec §7-adjacent, but never an
//! index snapshot): name, MIME type and caption, so the uploader and every
//! reader spell the same wire contract from one place.

use std::io::{Read, Write};

use flate2::Compression;
use flate2::read::GzDecoder;
use flate2::write::GzEncoder;
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// The only bundle layout this build writes or reads.
pub const BUNDLE_VERSION: u32 = 1;

/// Gzip bytes over this size are refused before decompression starts.
pub const MAX_COMPRESSED_BYTES: usize = 16 * 1024 * 1024;
/// Decompressed JSON over this size is refused mid-read, before it is ever
/// held whole in memory — the usual gzip-bomb guard.
pub const MAX_DECOMPRESSED_BYTES: u64 = 64 * 1024 * 1024;
/// One track's cue text over this size is refused after parsing.
pub const MAX_TRACK_BYTES: usize = 4 * 1024 * 1024;

/// One subtitle track inside a bundle. Field order is wire order.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct BundleTrack {
    pub lang: String,
    pub forced: bool,
    pub sdh: bool,
    pub label: String,
    /// `embedded` or `sidecar` — where the uploader read this track from.
    pub source: String,
    /// The original codec (`subrip`, `ass`, `mov_text`, `webvtt`, `srt`,
    /// `vtt`), kept for reference; every reader plays `vtt` regardless.
    pub codec: String,
    /// WebVTT text, ready to hand a player.
    pub vtt: String,
}

/// The bundle itself: every track for one set, self-describing.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct Bundle {
    pub v: u32,
    pub set: String,
    pub tracks: Vec<BundleTrack>,
}

#[derive(Error, Debug, PartialEq, Eq)]
pub enum BundleError {
    #[error("bundle is {0} bytes compressed, over the {MAX_COMPRESSED_BYTES} byte limit")]
    TooLarge(usize),
    #[error("bundle is not valid gzip")]
    NotGzip,
    #[error("bundle decompresses past the {MAX_DECOMPRESSED_BYTES} byte limit")]
    Bomb,
    #[error("bundle JSON: {0}")]
    Json(String),
    #[error("bundle version {0} is newer than this reader understands")]
    UnsupportedVersion(u32),
    #[error("a track is {0} bytes, over the {MAX_TRACK_BYTES} byte limit")]
    TrackTooLarge(usize),
}

/// gzips `bundle` as JSON.
///
/// # Panics
/// Never, in practice: every field is a plain string, bool or vec, and
/// writing to an in-memory buffer cannot fail.
#[must_use]
pub fn encode(bundle: &Bundle) -> Vec<u8> {
    let json = serde_json::to_vec(bundle).expect("bundle fields always serialize");
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    encoder
        .write_all(&json)
        .expect("writing to an in-memory buffer cannot fail");
    encoder
        .finish()
        .expect("finishing an in-memory gzip stream cannot fail")
}

/// Decodes and validates a bundle read from the channel: an attacker or a
/// corrupt upload controls every byte of `bytes` before this returns.
///
/// # Errors
/// Refuses oversized input at every stage — compressed, decompressed, and
/// per track — non-gzip data, unparsable JSON, and a version newer than
/// [`BUNDLE_VERSION`].
pub fn decode(bytes: &[u8]) -> Result<Bundle, BundleError> {
    if bytes.len() > MAX_COMPRESSED_BYTES {
        return Err(BundleError::TooLarge(bytes.len()));
    }
    let mut json = Vec::new();
    GzDecoder::new(bytes)
        .take(MAX_DECOMPRESSED_BYTES + 1)
        .read_to_end(&mut json)
        .map_err(|_| BundleError::NotGzip)?;
    if json.len() as u64 > MAX_DECOMPRESSED_BYTES {
        return Err(BundleError::Bomb);
    }
    let bundle: Bundle =
        serde_json::from_slice(&json).map_err(|err| BundleError::Json(err.to_string()))?;
    if bundle.v != BUNDLE_VERSION {
        return Err(BundleError::UnsupportedVersion(bundle.v));
    }
    for track in &bundle.tracks {
        if track.vtt.len() > MAX_TRACK_BYTES {
            return Err(BundleError::TrackTooLarge(track.vtt.len()));
        }
    }
    Ok(bundle)
}

/// Whether `s` is the lowercase 64-hex shape a bundle's `sha256` must have
/// before it becomes a file name.
#[must_use]
pub fn valid_sha256(s: &str) -> bool {
    crate::package::is_lower_hex(s, 64)
}

/// What every bundle caption starts with. A part caption starts `#mlib v=`
/// and an index snapshot `#mlib-index`; this prefix collides with neither,
/// so a reader tells all three apart by prefix alone — `rescan` and the
/// index-snapshot search both skip a bundle message without special-casing
/// it, since neither prefix matches.
pub const SUBS_CAPTION_PREFIX: &str = "#mlib-subs";
const SUBS_MARKER: &str = "#mlib-subs v=1";

/// MIME type a bundle document is sent under.
pub const SUBS_MIME_TYPE: &str = "application/gzip";

/// The channel file name for a set's bundle.
#[must_use]
pub fn bundle_file_name(set_id: &str) -> String {
    format!("{set_id}.subs.json.gz")
}

#[derive(Serialize)]
struct SubsCaptionBody<'a> {
    set: &'a str,
}

/// The caption a bundle document carries: the marker, then one line of JSON
/// naming the set it belongs to.
#[must_use]
pub fn render_caption(set_id: &str) -> String {
    let body = SubsCaptionBody { set: set_id };
    let json = serde_json::to_string(&body).expect("one string always serializes");
    format!("{SUBS_MARKER}\n{json}")
}

#[cfg(test)]
#[path = "subtitle_bundle_tests.rs"]
mod tests;
