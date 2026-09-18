//! ffprobe inspection, HDR/quality classification, faststart detection and remux.

pub mod classify;
pub mod direct_play;
pub mod inspect;
pub mod mp4_atoms;
pub mod prepare_check;
pub mod prepare_plan;
pub mod remux;
pub mod streams;
/// ffmpeg-built fixtures shared by unit and integration tests; unused at runtime.
pub mod test_fixtures;
