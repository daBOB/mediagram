//! Subtitles for a finished set: German and English text tracks, embedded or
//! beside the video, read out to WebVTT and sent to the channel as one
//! bundle that the index points at.
//!
//! Runs once a set is complete, never at planning, and never fails the
//! upload: a set without a bundle is complete, and the backfill can give it
//! one later.

mod arrange;
mod attach;
mod extract;
mod select;
mod sidecars;

pub use arrange::label as track_label;
pub use attach::{Attached, Input, attach, attach_and_report};
