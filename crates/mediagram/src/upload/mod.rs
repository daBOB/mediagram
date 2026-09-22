//! Getting a file into the channel: planning it as a set, then streaming its
//! parts — a hashing byte-range reader, the Telegram transport that sends
//! it, and the resumable per-set pipeline.

pub mod adopt;
pub mod finish;
pub mod finish_set;
pub mod lock;
pub mod new_set;
pub mod part_reader;
mod part_upload;
pub mod pipeline;
pub mod plan;
pub mod plan_document;
pub mod plan_set;
pub mod progress;
pub mod progress_line;
pub mod transport;
