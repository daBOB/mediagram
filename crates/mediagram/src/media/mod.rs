//! What a media file is and what can be done to it: one ffprobe report read
//! as caption fields or as streams, HDR/quality classification, the rules a
//! file name follows, faststart detection and remux, the direct-play policy,
//! `prepare`'s pipeline, and the progress ffmpeg reports while it works.

pub mod classify;
pub mod direct_play;
pub mod ffmpeg_progress;
pub mod file_names;
pub mod inspect;
pub mod mp4_atoms;
pub mod prepare;
pub mod probe;
pub mod remux;
pub mod show_episodes;
pub mod streams;
/// ffmpeg-built fixtures shared by unit and integration tests; unused at runtime.
pub mod test_fixtures;
pub mod video_files;
