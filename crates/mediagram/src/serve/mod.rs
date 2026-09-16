//! The playback server: a local HTTP API exposing what is playable and the
//! bytes of a set as one virtual file. Everything downstream (the browser,
//! ffmpeg) is a client of this, and nothing else talks to Telegram.

pub mod catalog;
pub mod range;
pub mod response;
pub mod routes;
pub mod stream;
pub mod telegram;
