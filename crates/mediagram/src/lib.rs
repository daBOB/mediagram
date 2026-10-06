//! mediagram library surface: everything the CLI binary and the integration
//! tests share. The binary in `main.rs` only parses arguments and dispatches.

// The integration tests' fakes name this crate `mediagram`, as any outside
// crate would; this lets the unit tests compile those same files in place.
#[cfg(test)]
extern crate self as mediagram;

pub mod app_release;
pub mod channel_index;
pub mod clock;
pub mod commands;
pub mod config;
pub mod course;
pub mod edit;
pub mod export;
pub mod index;
pub mod media;
pub mod metadata;
pub mod paths;
pub mod remove;
pub mod serve;
pub mod subtitles;
pub mod telegram;
pub mod term;
#[cfg(test)]
pub(crate) mod test_env;
#[cfg(test)]
pub(crate) mod test_fakes;
pub mod upload;
pub mod verify;
