//! The playback server: a local HTTP API exposing what is playable and the
//! bytes of a set as one virtual file. Everything downstream (the browser,
//! ffmpeg) is a client of this, and nothing else talks to Telegram.

pub use mediagram_core::catalog;
pub use mediagram_core::range;
pub use mediagram_core::stream;
pub use mediagram_core::telegram;
pub mod response;
pub mod routes;
