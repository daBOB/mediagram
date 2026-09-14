//! ffprobe inspection, HDR/quality classification, faststart detection and remux.

pub mod classify;
pub mod inspect;
pub mod mp4_atoms;
pub mod remux;
/// ffmpeg-built fixtures shared by unit and integration tests; unused at runtime.
pub mod test_fixtures;
