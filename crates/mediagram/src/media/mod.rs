//! ffprobe inspection, HDR/quality classification, faststart detection, remux,
//! and the progress ffmpeg reports while it works.

pub mod classify;
pub mod direct_play;
pub mod ffmpeg_progress;
pub mod inspect;
pub mod mp4_atoms;
pub mod prepare_check;
pub mod prepare_paths;
pub mod prepare_plan;
pub mod remux;
pub mod show_episodes;
pub mod streams;
pub mod video_files;
/// ffmpeg-built fixtures shared by unit and integration tests; unused at runtime.
pub mod test_fixtures;
