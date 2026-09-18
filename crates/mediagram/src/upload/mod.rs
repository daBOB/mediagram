//! Streaming part upload: a hashing byte-range reader, the Telegram
//! transport that sends it, and the resumable per-set pipeline.

pub mod adopt;
pub mod part_reader;
pub mod pipeline;
pub mod progress;
pub mod transport;
